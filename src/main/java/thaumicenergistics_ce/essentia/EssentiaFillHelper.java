package thaumicenergistics_ce.essentia;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IItemEssentia;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThELog;

public final class EssentiaFillHelper {

    private EssentiaFillHelper() {}

    public static final int JAR_CAPACITY = TcRegistry.jarCapacity();

    public static final int PHIAL_CAPACITY = TcRegistry.phialCapacity();

    public static boolean isSupportedContainer(ItemStack stack) {
        return TcRegistry.isEssentiaContainer(stack);
    }

    public static boolean isContainerEmpty(ItemStack stack) {
        return isSupportedContainer(stack) && contents(stack) == null;
    }

    public static boolean fillFromNetwork(
            Level level,
            MEStorage storage,
            IEnergySource energy,
            IActionSource source,
            Player player,
            ItemStack carried,
            Identifier aspectId) {
        if (carried.isEmpty() || aspectId == null) {
            return false;
        }
        if (!isSupportedContainer(carried)) {
            log("fill {} refused: held item is not a jar or a phial", aspectId);
            return false;
        }

        AEssentiaKey key = AEssentiaKey.of(aspectId);
        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
        if (available <= 0) {
            log("fill {} refused: the network reports {} available for {}", aspectId, available, key);
            dumpEssentia(storage);
            return false;
        }

        if (contents(carried) != null) {
            log("fill {} refused: the held {} already holds {}", aspectId, carried.getItem(), contents(carried));
            return false;
        }

        if (TcRegistry.isPhial(carried)) {
            Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
            if (aspect == null) {
                log("fill {} refused: the id resolves to no aspect in this level", aspectId);
                return false;
            }
            if (available < PHIAL_CAPACITY) {
                log("fill {} refused: a phial needs {} and the network holds {}", aspectId,
                        PHIAL_CAPACITY, available);
                return false;
            }
            long taken = storage.extract(key, PHIAL_CAPACITY, Actionable.MODULATE, source);
            if (taken < PHIAL_CAPACITY) {
                if (taken > 0) {
                    storage.insert(key, taken, Actionable.MODULATE, source);
                }
                return false;
            }
            carried.shrink(1);
            give(player, TcRegistry.filledPhial(aspect, PHIAL_CAPACITY));
            log("fill {} ok: {} into a phial", aspectId, PHIAL_CAPACITY);
            return true;
        }

        if (carried.getCapability(EssentiaCapabilities.CONTAINER) == null) {
            return false;
        }
        long wanted = Math.min(available, JAR_CAPACITY);
        long taken = storage.extract(key, wanted, Actionable.MODULATE, source);
        if (taken <= 0) {
            return false;
        }
        Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
        if (aspect == null) {
            storage.insert(key, taken, Actionable.MODULATE, source);
            return false;
        }
        ItemStack filled = carried.copyWithCount(1);
        IItemEssentia view = filled.getCapability(EssentiaCapabilities.CONTAINER);
        if (view == null) {
            storage.insert(key, taken, Actionable.MODULATE, source);
            return false;
        }
        carried.shrink(1);
        view.setAspects(AspectList.of(new AspectInstance(aspect, (int) taken)));
        give(player, filled);
        log("fill {} ok: {} of {} into a jar", aspectId, taken, available);
        return true;
    }

    private static void log(String message, Object... args) {
        ThELog.LOG.info("[essentia-terminal] " + message, args);
    }

    public static void dumpEssentia(MEStorage storage) {
        int total = 0;
        int aspects = 0;
        for (var entry : storage.getAvailableStacks()) {
            total++;
            if (entry.getKey() instanceof AEssentiaKey) {
                aspects++;
                if (aspects <= 24) {
                    log("  network holds {} x {}", entry.getLongValue(), entry.getKey());
                }
            }
        }
        log("  network holds {} aspects, and {} keys in all", aspects, total);
    }

    public record DepositResult(ItemStack remaining, List<ItemStack> overflow) {
        private static DepositResult unchanged(ItemStack stack) {
            return new DepositResult(stack, List.of());
        }
    }

    public static @Nullable DepositResult emptyIntoNetwork(
            MEStorage storage,
            IEnergySource energy,
            IActionSource source,
            ItemStack stack) {
        if (!isSupportedContainer(stack)) {
            log("deposit refused: {} is not a jar or a phial", stack.getItem());
            return null;
        }
        IItemEssentia container = stack.getCapability(EssentiaCapabilities.CONTAINER);
        if (container == null) {
            log("deposit refused: {} carries no essentia container capability", stack.getItem());
            return null;
        }
        int count = stack.getCount();
        if (count <= 0) {
            return DepositResult.unchanged(stack);
        }
        AspectList aspects = container.getAspects();
        if (aspects == null || aspects.isEmpty()) {
            log("deposit refused: {} x{} reports no aspects at all ({})", stack.getItem(), count, aspects);
            return DepositResult.unchanged(stack);
        }
        log("deposit asked: {} x{} holds {}", stack.getItem(), count, aspects);

        List<AEssentiaKey> keys = new ArrayList<>(aspects.size());
        List<Long> totals = new ArrayList<>(aspects.size());
        for (AspectInstance entry : aspects.entries()) {
            int perItem = entry.amount();
            if (perItem <= 0) {
                continue;
            }
            Identifier id = entry.aspect().unwrapKey().map(key -> key.identifier()).orElse(null);
            if (id == null) {
                log("deposit refused: {} is not backed by the registry, so it has no id to store",
                        entry.aspect());
                return DepositResult.unchanged(stack);
            }
            long total = (long) perItem * count;
            long accepted = storage.insert(AEssentiaKey.of(id), total, Actionable.SIMULATE, source);
            if (accepted < total) {
                log("deposit refused: the network takes {} of the {} it was asked for {}", accepted, total, id);
                return DepositResult.unchanged(stack);
            }
            keys.add(AEssentiaKey.of(id));
            totals.add(total);
        }
        if (keys.isEmpty()) {
            return DepositResult.unchanged(stack);
        }

        double power = energy.extractAEPower(1000, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        log("deposit: {} AE available, moving {} key(s)", power, keys.size());

        long[] inserted = new long[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            long moved = StorageHelper.poweredInsert(energy, storage, keys.get(i), totals.get(i), source);
            inserted[i] = moved;
            if (moved < totals.get(i)) {
                log("deposit stopped: only {} of {} {} made it in", moved, totals.get(i), keys.get(i));
                boolean restored = true;
                for (int j = 0; j <= i; j++) {
                    if (inserted[j] == 0) {
                        continue;
                    }
                    long withdrawn = storage.extract(keys.get(j), inserted[j], Actionable.MODULATE, source);
                    inserted[j] -= Math.clamp(withdrawn, 0L, inserted[j]);
                    restored &= inserted[j] == 0;
                }
                return restored ? DepositResult.unchanged(stack) : remainingContainers(stack, aspects, keys, inserted);
            }
        }

        log("deposit ok: {} key(s) of {} x{} moved into the network", keys.size(), stack.getItem(), count);
        ItemStack empty = stack.copy();
        empty.getCapability(EssentiaCapabilities.CONTAINER).setAspects(AspectList.EMPTY);
        return new DepositResult(empty, List.of());
    }

    private static DepositResult remainingContainers(
            ItemStack original, AspectList contents, List<AEssentiaKey> keys, long[] inserted) {
        List<AspectInstance> entries = contents.entries().stream().filter(entry -> entry.amount() > 0).toList();
        long[] remaining = new long[keys.size()];
        for (int i = 0; i < remaining.length; i++) {
            remaining[i] = (long) entries.get(i).amount() * original.getCount() - inserted[i];
        }
        List<ItemStack> containers = new ArrayList<>();
        for (int item = 0; item < original.getCount(); item++) {
            AspectList left = AspectList.EMPTY;
            for (int i = 0; i < remaining.length; i++) {
                int amount = (int) Math.min(entries.get(i).amount(), remaining[i]);
                if (amount > 0) {
                    left = left.add(entries.get(i).aspect(), amount);
                    remaining[i] -= amount;
                }
            }
            ItemStack container = original.copyWithCount(1);
            container.getCapability(EssentiaCapabilities.CONTAINER).setAspects(left);
            ItemStack previous = containers.isEmpty() ? ItemStack.EMPTY : containers.getLast();
            if (!previous.isEmpty() && ItemStack.isSameItemSameComponents(previous, container)
                    && previous.getCount() < previous.getMaxStackSize()) {
                previous.grow(1);
            } else {
                containers.add(container);
            }
        }
        return new DepositResult(containers.removeFirst(), List.copyOf(containers));
    }

    private static @Nullable AspectList contents(ItemStack stack) {
        IItemEssentia container = stack.getCapability(EssentiaCapabilities.CONTAINER);
        if (container != null) {
            AspectList aspects = container.getAspects();
            if (aspects != null && !aspects.isEmpty()) {
                return aspects;
            }
        }
        return null;
    }

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
