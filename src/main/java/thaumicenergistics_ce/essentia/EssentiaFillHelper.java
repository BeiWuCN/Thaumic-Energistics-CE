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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThELog;

/**
 * 在容器物品与 ME 网络之间搬运源质，移植自参考构建的
 * {@code EssentiaFillHelper}：先模拟后执行、部分拒绝时的回滚
 * 以及缩小前的复制，都是为了防复制或丢失。瓶子要么整瓶填充
 * （{@code TcRegistry.phialCapacity()}）要么完全不填，而罐子按网络允许
 * 的量填充且不被消耗。标签、水晶和魔力豆也实现了
 * {@code IEssentiaContainerItem}，所以 {@link #isSupportedContainer} 是唯一的闸门。
 */
public final class EssentiaFillHelper {

    private EssentiaFillHelper() {}

    /** 罐子的容量。取自 Thaumaturge 自身的数值——见 {@code TcRegistry.jarCapacity()}。 */
    public static final int JAR_CAPACITY = TcRegistry.jarCapacity();

    public static final int PHIAL_CAPACITY = TcRegistry.phialCapacity();

    /** 不用 {@code instanceof IEssentiaContainerItem}：标签、水晶和魔力豆不可填充。 */
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
            ResourceLocation aspectId) {
        if (carried.isEmpty() || aspectId == null) {
            return false;
        }
        if (!isSupportedContainer(carried)) {
            log("fill {} refused: held item is not a jar or a phial", aspectId);
            return false;
        }

        AEssentiaKey key = AEssentiaKey.of(aspectId);
        // 用模拟抽取，而不是读网络 key 计数的快照：那个计数是每个已装存储元件
        // 持有的每个 key 的完整拷贝，而这段代码在玩家可以随意重复的点击上运行。
        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
        if (available <= 0) {
            log("fill {} refused: the network reports {} available for {}", aspectId, available, key);
            dumpEssentia(storage);
            tell(player, "thaumicenergistics_ce.gui.essentia.network_empty", key);
            return false;
        }

        // 两种容器都不接受第二种要素：已经装了东西的瓶子会在其上被抽取
        // 整整一瓶的量，那就是被销毁的源质。
        if (contents(carried) != null) {
            log("fill {} refused: the held {} already holds {}", aspectId, carried.getItem(), contents(carried));
            tell(player, "thaumicenergistics_ce.gui.essentia.container_not_empty");
            return false;
        }

        // 瓶子要么整瓶填充要么完全不填：返回的是另一个物品堆，而不是被加满的那个。
        if (TcRegistry.isPhial(carried)) {
            Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
            if (aspect == null) {
                log("fill {} refused: the id resolves to no aspect in this level", aspectId);
                tell(player, "thaumicenergistics_ce.gui.essentia.no_aspect", aspectId);
                return false;
            }
            if (available < PHIAL_CAPACITY) {
                // 玩家最常遇到的拒绝：瓶子整瓶填充，所以少于 8 就填不了。
                log("fill {} refused: a phial needs {} and the network holds {}", aspectId,
                        PHIAL_CAPACITY, available);
                tell(player, "thaumicenergistics_ce.gui.essentia.phial_needs", PHIAL_CAPACITY, available);
                return false;
            }
            long taken = storage.extract(key, PHIAL_CAPACITY, Actionable.MODULATE, source);
            if (taken < PHIAL_CAPACITY) {
                // 把已取出的部分放回去，这样部分抽取就不会销毁源质。
                if (taken > 0) {
                    storage.insert(key, taken, Actionable.MODULATE, source);
                }
                log("fill {} refused: the network gave {} of the {} a phial needs", aspectId,
                        taken, PHIAL_CAPACITY);
                tell(player, "thaumicenergistics_ce.gui.essentia.network_empty", key);
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
            log("fill {} refused: the network gave none of the {} offered", aspectId, wanted);
            tell(player, "thaumicenergistics_ce.gui.essentia.network_empty", key);
            return false;
        }
        Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
        if (aspect == null) {
            storage.insert(key, taken, Actionable.MODULATE, source);
            log("fill {} refused: the id resolves to no aspect in this level", aspectId);
            tell(player, "thaumicenergistics_ce.gui.essentia.no_aspect", aspectId);
            return false;
        }
        // 在手牌物品堆缩小之前复制：当堆叠数量为 1 时，shrink 会留下空堆
        // 单例，而 copy() 会把 EMPTY 本身交回去，破坏这个共享常量。
        ItemStack filled = carried.copyWithCount(1);
        carried.shrink(1);
        container.setAspects(filled, AspectList.of(new AspectInstance(aspect, (int) taken)));
        give(player, filled);
        log("fill {} ok: {} of {} into a jar", aspectId, taken, available);
        return true;
    }

    private static void log(String message, Object... args) {
        ThELog.LOG.info("[essentia-terminal] " + message, args);
    }

    /**
     * 把拒绝同时告诉玩家和日志。罐子一直保持满的样子，与
     * 从未执行的手势完全相同，而这两者之一必是 bug，所以每次拒绝都要自己说出来。
     */
    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
    }

    /**
     * 打印存储服务声称持有的所有内容；写它是为了回答为什么屏幕列出了几十种
     * 要素，而服务端的 available-stacks 调用除最后一次存入外全都答 0。
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
     * 把源质容器倒空到网络中。先做模拟：一堆罐子共用一个
     * contents 标签，所以整堆作为一个数量进入，任何一处被拒绝就什么都不移动。
     * @return 用于放在该容器位置上的物品堆；它不是容器时为 {@code null}
     */
    public static @Nullable ItemStack emptyIntoNetwork(
            MEStorage storage,
            IEnergySource energy,
            IActionSource source,
            Player player,
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
                // 没有注册表支撑，所以它没有可存入的 id，整个容器原样保留：
                // 跳过该条目却照样倒空，就等于把它丢弃。
                log("store refused: an aspect of the held {} has no id", stack.getHoverName().getString());
                return stack;
            }
            long total = (long) perItem * count;
            long accepted = storage.insert(AEssentiaKey.of(id), total, Actionable.SIMULATE, source);
            if (accepted < total) {
                // 没有地方把它全部装下：宁可让容器原样不动，也不要只倒空一半。这正是
                // 已满或被过滤的存储元件给出的拒绝，也是过去默默发生的那一种。
                log("store {} refused: the network can take {} of {}", id, accepted, total);
                dumpEssentia(storage);
                tell(player, "thaumicenergistics_ce.gui.essentia.network_full");
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
                // 中途电力耗尽：把这一条以及它之前的全部放回去。
                if (moved > 0) {
                    storage.insert(keys.get(i), moved, Actionable.MODULATE, source);
                }
                for (int j = 0; j < i; j++) {
                    storage.insert(keys.get(j), totals.get(j), Actionable.MODULATE, source);
                }
                log("store {} refused: the powered insert moved {} of {}", keys.get(i), moved, totals.get(i));
                dumpEssentia(storage);
                tell(player, "thaumicenergistics_ce.gui.essentia.no_power");
                return stack;
            }
        }

        // 已倒空。罐子存活为空罐子，瓶子被消耗，两者物品 id 都与输入相同
        // （见类注释），所以输入的一份空副本就是全部。
        for (int i = 0; i < keys.size(); i++) {
            log("store {} ok: {} into the network", keys.get(i), totals.get(i));
        }
        if (TcRegistry.isPhial(stack)) {
            return TcRegistry.emptyPhials(count);
        }
        return new ItemStack(stack.getItem(), count);
    }

    private static @Nullable AspectList contents(ItemStack stack) {
        if (stack.getItem() instanceof IEssentiaContainerItem container) {
            AspectList aspects = container.getAspects(stack);
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
