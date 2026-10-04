package thaumicenergistics_ce.essentia;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaContainerItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * Moving essentia between a container item and the ME network.
 * <ul><li>A port of the reference build's {@code EssentiaFillHelper}. Simulate-then-execute, the rollback
 * on a partial refusal and the copy before shrink all guard against duplication or loss.</li>
 * <li>A phial is filled whole ({@code TcRegistry.phialCapacity()}) or not at all; a jar as far as the
 * network allows, and is not consumed.</li>
 * <li>The label, crystal and mana bean also implement {@code IEssentiaContainerItem}, so
 * {@link #isSupportedContainer} is the one gate.</li></ul> */
public final class EssentiaFillHelper {

    private EssentiaFillHelper() {}

    /** How much a jar holds. Thaumaturge's own figure - see {@code TcRegistry.jarCapacity()}. */
    public static final int JAR_CAPACITY = TcRegistry.jarCapacity();

    /** How much one phial holds. Thaumaturge's own figure - see {@code TcRegistry.phialCapacity()}. */
    public static final int PHIAL_CAPACITY = TcRegistry.phialCapacity();

    /** Not {@code instanceof IEssentiaContainerItem}: the label, crystal and mana bean are not fillable. */
    public static boolean isSupportedContainer(ItemStack stack) {
        return TcRegistry.isEssentiaContainer(stack);
    }

    /** Whether the stack is a container that is currently empty - what a fill wants. */
    public static boolean isContainerEmpty(ItemStack stack) {
        return isSupportedContainer(stack) && contents(stack) == null;
    }

    /** Fills a container from the network. Server side only; {@code level} resolves the aspect, which a
     * key names but does not carry. */
    public static boolean fillFromNetwork(
            Level level,
            MEStorage storage,
            IEnergySource energy,
            IActionSource source,
            Player player,
            ItemStack carried,
            ResourceLocation aspectId) {
        if (carried.isEmpty() || aspectId == null) {
            return false;
        }
        if (!isSupportedContainer(carried)) {
            log("fill {} refused: held item is not a jar or a phial", aspectId);
            return false;
        }

        AEssentiaKey key = AEssentiaKey.of(aspectId);
        // A simulated extract, not a read of the network's key counter: that counter is a complete copy of
        // every key every mounted cell holds, and this runs on a click a player can repeat at will.
        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
        if (available <= 0) {
            log("fill {} refused: the network reports {} available for {}", aspectId, available, key);
            dumpEssentia(storage);
            return false;
        }

        // Neither container takes a second aspect: a phial that already held something would have a whole
        // phial's worth extracted over it, which is essentia destroyed.
        if (contents(carried) != null) {
            log("fill {} refused: the held {} already holds {}", aspectId, carried.getItem(), contents(carried));
            return false;
        }

        // A phial is filled whole or not at all: it comes back as a different stack, not a topped-up one.
        if (TcRegistry.isPhial(carried)) {
            Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
            if (aspect == null) {
                log("fill {} refused: the id resolves to no aspect in this level", aspectId);
                return false;
            }
            if (available < PHIAL_CAPACITY) {
                // The refusal a player meets most often: a phial is filled whole, so fewer than 8 cannot.
                log("fill {} refused: a phial needs {} and the network holds {}", aspectId,
                        PHIAL_CAPACITY, available);
                return false;
            }
            long taken = storage.extract(key, PHIAL_CAPACITY, Actionable.MODULATE, source);
            if (taken < PHIAL_CAPACITY) {
                // Put back what came out, so a partial extraction cannot destroy essentia.
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

        if (!(carried.getItem() instanceof IEssentiaContainerItem container)) {
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
        // Copied before the hand stack shrinks: on a stack of one, shrink would leave the empty-stack
        // singleton and copy() would hand back EMPTY itself, corrupting the shared constant.
        ItemStack filled = carried.copyWithCount(1);
        carried.shrink(1);
        container.setAspects(filled, AspectList.of(new AspectInstance(aspect, (int) taken)));
        give(player, filled);
        log("fill {} ok: {} of {} into a jar", aspectId, taken, available);
        return true;
    }

    /** One line per fill attempt, with the number that decided it. Cheap: once per player click. */
    private static void log(String message, Object... args) {
        ThaumicEnergistics.LOG.info("[essentia-terminal] " + message, args);
    }

    /**
     * Prints everything the storage service says it holds; written to answer why the screen listed dozens of
     * aspects while the server's available-stacks call answered 0 for all but the last deposit.
     */
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

    /**
     * Empties an essentia container into the network. Simulated first: a stack of jars shares one
     * contents tag, so the whole stack goes in as one amount and a refusal anywhere moves nothing.
     * @return the stack to put in the container's place, or {@code null} if it is not a container
     */
    public static @Nullable ItemStack emptyIntoNetwork(
            MEStorage storage,
            IEnergySource energy,
            IActionSource source,
            ItemStack stack) {
        if (!isSupportedContainer(stack)) {
            return null;
        }
        if (!(stack.getItem() instanceof IEssentiaContainerItem container)) {
            return null;
        }
        int count = stack.getCount();
        if (count <= 0) {
            return stack;
        }
        AspectList aspects = container.getAspects(stack);
        if (aspects == null || aspects.isEmpty()) {
            return stack;
        }

        List<AEssentiaKey> keys = new ArrayList<>(aspects.size());
        List<Long> totals = new ArrayList<>(aspects.size());
        for (AspectInstance entry : aspects.entries()) {
            int perItem = entry.amount();
            if (perItem <= 0) {
                continue;
            }
            ResourceLocation id = entry.aspect().unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) {
                // Not registry-backed, so it has no id to store under and the whole container is left alone:
                // skipping the entry and emptying anyway would discard it.
                return stack;
            }
            long total = (long) perItem * count;
            if (storage.insert(AEssentiaKey.of(id), total, Actionable.SIMULATE, source) < total) {
                // Nowhere to put all of it: leave the container alone rather than half-empty it.
                return stack;
            }
            keys.add(AEssentiaKey.of(id));
            totals.add(total);
        }
        if (keys.isEmpty()) {
            return stack;
        }

        for (int i = 0; i < keys.size(); i++) {
            long moved = StorageHelper.poweredInsert(energy, storage, keys.get(i), totals.get(i), source);
            if (moved < totals.get(i)) {
                // Power ran out part way: put back this entry and everything before it.
                if (moved > 0) {
                    storage.insert(keys.get(i), moved, Actionable.MODULATE, source);
                }
                for (int j = 0; j < i; j++) {
                    storage.insert(keys.get(j), totals.get(j), Actionable.MODULATE, source);
                }
                return stack;
            }
        }

        // Emptied. A jar survives as an empty jar and a phial is spent, both the same item id as the input
        // (see the class note), so an empty copy of the input is the whole of it.
        if (TcRegistry.isPhial(stack)) {
            return TcRegistry.emptyPhials(count);
        }
        return new ItemStack(stack.getItem(), count);
    }

    /** The aspects a container carries, or {@code null} when it carries none. */
    private static @Nullable AspectList contents(ItemStack stack) {
        if (stack.getItem() instanceof IEssentiaContainerItem container) {
            AspectList aspects = container.getAspects(stack);
            if (aspects != null && !aspects.isEmpty()) {
                return aspects;
            }
        }
        return null;
    }

    /** Puts a filled container in the player's inventory, or on the ground if there is no room. */
    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
