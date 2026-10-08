package thaumicenergistics_ce.essentia;

import appeng.api.config.Actionable;
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

/**
 * 在容器物品与 ME 网络之间搬源质，移植自参考构建的 {@code EssentiaFillHelper}。
 * 先模拟后执行，部分拒绝就回滚，缩小前先复制：防复制也防丢失。
 * 瓶子整瓶填（{@code TcRegistry.phialCapacity()}）或完全不填；罐子按网络允许的量填，不被消耗。
 * 标签、水晶、魔力豆也是 {@code IEssentiaContainerItem}，{@link #isSupportedContainer} 是唯一的闸门。
 */
public final class EssentiaFillHelper {

    private EssentiaFillHelper() {}

    /** 罐子容量，取 Thaumaturge 的数值（{@code TcRegistry.jarCapacity()}）。 */
    public static final int JAR_CAPACITY = TcRegistry.jarCapacity();

    public static final int PHIAL_CAPACITY = TcRegistry.phialCapacity();

    /** 不走 {@code instanceof IEssentiaContainerItem}：标签、水晶、魔力豆填不了。 */
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
        // 用模拟抽取。读 key 计数是每个存储元件一份完整拷贝，
        // 而这里跑在玩家能随便重复的点击上。
        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
        if (available <= 0) {
            log("fill {} refused: the network reports {} available for {}", aspectId, available, key);
            dumpEssentia(storage);
            return false;
        }

        // 容器里已有东西时不再接受第二种要素：照抽一整瓶就是白扔源质。
        if (contents(carried) != null) {
            log("fill {} refused: the held {} already holds {}", aspectId, carried.getItem(), contents(carried));
            return false;
        }

        // 瓶子整瓶填或完全不填；返回的是另一个物品堆，不是原来那个。
        if (TcRegistry.isPhial(carried)) {
            Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
            if (aspect == null) {
                log("fill {} refused: the id resolves to no aspect in this level", aspectId);
                return false;
            }
            if (available < PHIAL_CAPACITY) {
                // 最常见的拒绝：瓶子要整瓶，网络里不到 8 就填不了。
                log("fill {} refused: a phial needs {} and the network holds {}", aspectId,
                        PHIAL_CAPACITY, available);
                return false;
            }
            long taken = storage.extract(key, PHIAL_CAPACITY, Actionable.MODULATE, source);
            if (taken < PHIAL_CAPACITY) {
                // 把已取出的部分放回去，别让部分抽取销毁源质。
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
        // 手牌堆缩小之前先复制。数量为 1 时 [shrink] 留下空堆单例，
        // [copy()] 会把 [EMPTY] 本身交回来，毁掉这个共享常量。
        ItemStack filled = carried.copyWithCount(1);
        // 能力是绑定在「被问的那个堆」上的视图，副本得有自己的；
        // 而且要在手牌堆缩小之前取到：
        // 等缩小之后才发现副本写不进去，赔掉的就是物品加源质。
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

    /**
     * 打印存储服务声称持有的东西。排查用：屏幕列出几十种要素，
     * 而服务端的 available-stacks 调用除最后一次存入外全答 0。
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
     * 把源质容器倒进网络。先模拟：一叠罐子共用同一份 contents 标签，
     * 所以整叠按一个量存入，任何一处被拒就一点都不动。
     * @return 放进容器位置的那个堆；不是容器时返回 {@code null}
     */
    public static @Nullable ItemStack emptyIntoNetwork(
            MEStorage storage,
            IEnergySource energy,
            IActionSource source,
            ItemStack stack) {
        if (!isSupportedContainer(stack)) {
            return null;
        }
        IItemEssentia container = stack.getCapability(EssentiaCapabilities.CONTAINER);
        if (container == null) {
            return null;
        }
        int count = stack.getCount();
        if (count <= 0) {
            return stack;
        }
        AspectList aspects = container.getAspects();
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
            Identifier id = entry.aspect().unwrapKey().map(key -> key.identifier()).orElse(null);
            if (id == null) {
                // 不由注册表支撑：没有 id 可以存，整个容器就原样留下；
                // 跳过这一条照样倒空，等于把它丢掉。
                return stack;
            }
            long total = (long) perItem * count;
            if (storage.insert(AEssentiaKey.of(id), total, Actionable.SIMULATE, source) < total) {
                // 没地方全放下：容器原样留下，不做半倒空。
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
                // 电力中途耗尽：这一条连同它之前的全部放回去。
                if (moved > 0) {
                    storage.insert(keys.get(i), moved, Actionable.MODULATE, source);
                }
                for (int j = 0; j < i; j++) {
                    storage.insert(keys.get(j), totals.get(j), Actionable.MODULATE, source);
                }
                return stack;
            }
        }

        // 倒空了。罐子留成空罐，瓶子被消耗，两者物品 id 都和输入相同
        // （见类注释），所以空的输入副本就是它的全部。
        if (TcRegistry.isPhial(stack)) {
            return TcRegistry.emptyPhials(count);
        }
        return new ItemStack(stack.getItem(), count);
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
