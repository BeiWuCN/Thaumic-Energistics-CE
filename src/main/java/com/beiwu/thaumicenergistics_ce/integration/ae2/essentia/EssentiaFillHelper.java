package com.beiwu.thaumicenergistics_ce.integration.ae2.essentia;

import appeng.api.stacks.AEItemKey;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import com.beiwu.thaumicenergistics_ce.util.ThELog;

/**
 * 在容器物品与 ME 网络之间搬源质，移植自参考构建的 {@code EssentiaFillHelper}。
 * 先模拟后执行，部分拒绝就回滚，缩小前先复制：防复制也防丢失。
 * 瓶子整瓶填（{@code TcRegistry.phialCapacity()}）或完全不填；罐子按网络允许的量填，不被消耗。
 * 要素水晶与魔力豆也注册了同一个容器能力，但写回时数量被截断成 1，当容器用会丢源质、
 * 无要素的魔力豆还会被定时器重新赋要素而能刷，所以已在 {@code TcRegistry.isEssentiaContainer}
 * 里按物品排除；{@link #isSupportedContainer} 是唯一的闸门。
 */
public final class EssentiaFillHelper {

    private EssentiaFillHelper() {}

    /** 罐子容量，取 Thaumaturge 的数值（{@code TcRegistry.jarCapacity()}）。 */
    public static final int JAR_CAPACITY = TcRegistry.jarCapacity();

    public static final int PHIAL_CAPACITY = TcRegistry.phialCapacity();

    /** 不走源质容器能力：标签、水晶、魔力豆填不了。 */
    public static boolean isSupportedContainer(ItemStack stack) {
        return TcRegistry.isEssentiaContainer(stack);
    }

    /**
     * 桶同款：手上没容器时，从网络里取一个空瓶子就地灌满，交回玩家。
     * <p>AE2 的流体终端点条目就是这么办的——看 {@code MEStorageMenu.tryFillContainerItem}
     * 头一段：光标空着、要的又是流体时，先从网络抽一个空桶放上去，再灌。这里照做，
     * 只是换成源质与瓶子。同样要求网络里真的有空瓶：凭空变一个出来不是同款。
     * <p>源质不到一整瓶，或网络里没有空瓶，都不做；已经抽出的空瓶退回网络，
     * 不留半个状态。
     */
    public static @Nullable ItemStack takeFilledPhial(
            Level level, MEStorage storage, IActionSource source, Player player, ResourceLocation aspectId) {
        if (aspectId == null) {
            return null;
        }
        AEssentiaKey key = AEssentiaKey.of(aspectId);
        Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
        if (key == null || aspect == null) {
            log("take {} refused: the id resolves to no aspect in this level", aspectId);
            tell(player, "thaumicenergistics_ce.gui.essentia.no_aspect", aspectId);
            return null;
        }

        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
        if (available < PHIAL_CAPACITY) {
            log("take {} refused: a phial needs {} and the network holds {}", aspectId,
                    PHIAL_CAPACITY, available);
            tell(player, "thaumicenergistics_ce.gui.essentia.phial_needs", PHIAL_CAPACITY, available);
            return null;
        }

        AEItemKey emptyPhial = AEItemKey.of(TcRegistry.emptyPhials(1));
        if (storage.extract(emptyPhial, 1, Actionable.SIMULATE, source) < 1) {
            log("take {} refused: the network holds no empty phial", aspectId);
            tell(player, "thaumicenergistics_ce.gui.essentia.no_empty_phial");
            return null;
        }

        if (storage.extract(emptyPhial, 1, Actionable.MODULATE, source) < 1) {
            // 模拟之后又抽不到：网络变了。此刻一点源质都还没扣，直接退出。
            log("take {} refused: the empty phial vanished between the check and the take", aspectId);
            tell(player, "thaumicenergistics_ce.gui.essentia.no_empty_phial");
            return null;
        }
        long taken = storage.extract(key, PHIAL_CAPACITY, Actionable.MODULATE, source);
        if (taken < PHIAL_CAPACITY) {
            // 先抽后模拟取不平：把空瓶和已抽的源质都放回去，不让部分抽取销毁东西。
            // 回滚本身失败只能靠日志留痕：这里已经没有更稳的补偿手段。
            if (storage.insert(emptyPhial, 1, Actionable.MODULATE, source) < 1) {
                log("take {} rollback lost an empty phial", aspectId);
            }
            if (taken > 0 && storage.insert(key, taken, Actionable.MODULATE, source) < taken) {
                log("take {} rollback lost {} of the {} essentia taken", aspectId, taken, PHIAL_CAPACITY);
            }
            log("take {} refused: the network gave {} of the {}", aspectId, taken, PHIAL_CAPACITY);
            tell(player, "thaumicenergistics_ce.gui.essentia.network_empty", key.getId());
            return null;
        }
        log("take {} ok: {} into a phial", aspectId, PHIAL_CAPACITY);
        return TcRegistry.filledPhial(aspect, PHIAL_CAPACITY);
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
        // 用模拟抽取。读 key 计数是每个存储元件一份完整拷贝，
        // 而这里跑在玩家能随便重复的点击上。
        long available = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
        if (available <= 0) {
            log("fill {} refused: the network reports {} available for {}", aspectId, available, key);
            dumpEssentia(storage);
            tell(player, "thaumicenergistics_ce.gui.essentia.network_empty", key.getId());
            return false;
        }

        // 容器里已有东西时不再接受第二种要素：照抽一整瓶就是白扔源质。
        if (contents(carried) != null) {
            log("fill {} refused: the held {} already holds {}", aspectId, carried.getItem(), contents(carried));
            tell(player, "thaumicenergistics_ce.gui.essentia.container_not_empty");
            return false;
        }

        // 瓶子整瓶填或完全不填；返回的是另一个物品堆，不是原来那个。
        if (TcRegistry.isPhial(carried)) {
            Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
            if (aspect == null) {
                log("fill {} refused: the id resolves to no aspect in this level", aspectId);
                tell(player, "thaumicenergistics_ce.gui.essentia.no_aspect", aspectId);
                return false;
            }
            if (available < PHIAL_CAPACITY) {
                // 最常见的拒绝：瓶子要整瓶，差一点也不填。
                log("fill {} refused: a phial needs {} and the network holds {}", aspectId,
                        PHIAL_CAPACITY, available);
                tell(player, "thaumicenergistics_ce.gui.essentia.phial_needs", PHIAL_CAPACITY, available);
                return false;
            }
            long taken = storage.extract(key, PHIAL_CAPACITY, Actionable.MODULATE, source);
            if (taken < PHIAL_CAPACITY) {
                // 把已取出的部分放回去，别让部分抽取销毁源质。
                if (taken > 0) {
                    storage.insert(key, taken, Actionable.MODULATE, source);
                }
                log("fill {} refused: the network gave {} of the {} a phial needs", aspectId,
                        taken, PHIAL_CAPACITY);
                tell(player, "thaumicenergistics_ce.gui.essentia.network_empty", key.getId());
                return false;
            }
            carried.shrink(1);
            give(player, TcRegistry.filledPhial(aspect, PHIAL_CAPACITY));
            log("fill {} ok: {} into a phial", aspectId, PHIAL_CAPACITY);
            return true;
        }

        IItemEssentia container = carried.getCapability(EssentiaCapabilities.CONTAINER);
        if (container == null) {
            return false;
        }
        long wanted = Math.min(available, JAR_CAPACITY);
        long taken = storage.extract(key, wanted, Actionable.MODULATE, source);
        if (taken <= 0) {
            log("fill {} refused: the network gave none of the {} offered", aspectId, wanted);
            tell(player, "thaumicenergistics_ce.gui.essentia.network_empty", key.getId());
            return false;
        }
        Holder<IAspect> aspect = AEssentiaKeyType.aspectOf(level, aspectId);
        if (aspect == null) {
            storage.insert(key, taken, Actionable.MODULATE, source);
            log("fill {} refused: the id resolves to no aspect in this level", aspectId);
            tell(player, "thaumicenergistics_ce.gui.essentia.no_aspect", aspectId);
            return false;
        }
        // 手牌堆缩小之前先复制。数量为 1 时 [shrink] 留下空堆单例，
        // [copy()] 会把 [EMPTY] 本身交回来，毁掉这个共享常量。
        ItemStack filled = carried.copyWithCount(1);
        // 能力是一份绑定到「被问的那个堆」的视图，副本得有自己的；
        // 而且要在手牌堆缩小之前取到：取不到时源质还能退回网络，之后就只能白丢。
        IItemEssentia filledView = filled.getCapability(EssentiaCapabilities.CONTAINER);
        if (filledView == null) {
            storage.insert(key, taken, Actionable.MODULATE, source);
            return false;
        }
        carried.shrink(1);
        filledView.setAspects(AspectList.of(new AspectInstance(aspect, (int) taken)));
        give(player, filled);
        log("fill {} ok: {} of {} into a jar", aspectId, taken, available);
        return true;
    }

    private static void log(String message, Object... args) {
        ThELog.LOG.info("[essentia-terminal] " + message, args);
    }

    /**
     * 拒绝同时告诉玩家和日志。罐子看起来一直满，跟手势没跑过一模一样，
     * 分不清就只能每次都出声。
     */
    private static void tell(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
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
     * 整堆倒进网络。先模拟：一堆罐子共用一个 [contents] 标签，
     * 整堆算一个数量，一处被拒就什么都不动。
     * @return 放在该容器位置上的物品堆；它不是容器时为 {@code null}
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
            ResourceLocation id = entry.aspect().unwrapKey().map(key -> key.location()).orElse(null);
            if (id == null) {
                // 没有 id 就没法存，整个容器原样留着；跳过该条目还照倒，就是把它丢了。
                log("store refused: an aspect of the held {} has no id", stack.getHoverName().getString());
                return stack;
            }
            long total = (long) perItem * count;
            long accepted = storage.insert(AEssentiaKey.of(id), total, Actionable.SIMULATE, source);
            if (accepted < total) {
                // 装不下就整个不动，不倒一半。这是已满或被过滤的存储元件给出的拒绝，
                // 以前这种拒绝是静默的。
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
                // 电力中途耗尽：这一条连同它之前的全部放回去。
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

        // 倒空了。罐子留成空罐子，瓶子被消耗，物品 id 与输入一样，
        // 拿这份空副本当输出就行。
        for (int i = 0; i < keys.size(); i++) {
            log("store {} ok: {} into the network", keys.get(i), totals.get(i));
        }
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
