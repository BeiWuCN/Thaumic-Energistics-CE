package thaumicenergistics_ce.golem;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageHelper;
import appeng.api.util.DimensionalBlockPos;
import appeng.blockentity.networking.WirelessAccessPointBlockEntity;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.content.golem.EntityThaumaturgeGolem;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 单个傀儡通往 ME 网络的一条已解析路径：指向某个接入点的链接，用完即弃。
 * 背包只保存一个 {@link GlobalPos}，别无他物，因此那里的方块必须是一个
 * 接入点，且其网格在傀儡的可达范围内；每次操作都重新解析，这样一旦走开
 * 就会立刻被发现。只搬运物品，而付不起能量的网络什么也不搬，
 * 因为这一步由 AE2 的耗能辅助类判定。
 */
public final class GolemWirelessLink {

    /** 每次操作的物品数，按傀儡等级。参照实现自己的一组数值，按等级选取。 */
    private static final int[] ITEM_RATES = {8, 24, 32};

    private final MEStorage storage;
    private final IEnergyService energy;
    private final IActionSource source;

    private GolemWirelessLink(MEStorage storage, IEnergyService energy, IActionSource source) {
        this.storage = storage;
        this.energy = energy;
        this.source = source;
    }

    @Nullable
    public static GolemWirelessLink open(EntityThaumaturgeGolem golem, GlobalPos target) {
        Level level = golem.level();
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        ServerLevel linkedLevel = serverLevel.getServer().getLevel(target.dimension());
        if (linkedLevel == null) {
            return null;
        }

        BlockEntity blockEntity = Platform.getTickingBlockEntity(linkedLevel, target.pos());
        if (!(blockEntity instanceof IWirelessAccessPoint accessPoint)) {
            return null;
        }
        IGrid grid = accessPoint.getGrid();
        if (grid == null || !inRange(serverLevel, grid, golem)) {
            return null;
        }

        // 这次传输记在接入点账上，而它充当动作宿主，AE2 的安全与
        // 频道统计正是这样要求任何代表网格行事的对象的。
        return new GolemWirelessLink(
                grid.getStorageService().getInventory(),
                grid.getEnergyService(),
                IActionSource.ofMachine(accessPoint));
    }

    /**
     * 把物品堆里网络愿意收下的那部分放进去，并按该数量缩减这个物品堆。这样做是安全的，
     * 因为 {@code getCarrying()} 交出的是傀儡自己正在使用的活物品堆，所以调用方会被就地缩减。
     *
     * @return 被接收的物品数量；网络已满或电力耗尽时为 0。
     */
    public long insert(ItemStack stack, int limit) {
        AEItemKey key = AEItemKey.of(stack);
        if (key == null || stack.isEmpty()) {
            return 0L;
        }
        long inserted = StorageHelper.poweredInsert(energy, storage, key, Math.min(stack.getCount(), limit), source);
        if (inserted > 0L) {
            stack.shrink((int) inserted);
        }
        return inserted;
    }

    public static int itemRate(EntityThaumaturgeGolem golem) {
        int rank = Math.max(0, Math.min(ITEM_RATES.length - 1, golem.getProperties().getRank()));
        return ITEM_RATES[rank];
    }

    /**
     * 傀儡是否处在其所链接网格上某个活动接入点的范围内。所查询的类
     * 必须是具体类——见 {@code owner.getClass()} 处的说明。
     */
    private static boolean inRange(ServerLevel level, IGrid grid, EntityThaumaturgeGolem golem) {
        for (WirelessAccessPointBlockEntity accessPoint : grid.getMachines(WirelessAccessPointBlockEntity.class)) {
            DimensionalBlockPos location = accessPoint.getLocation();
            if (!accessPoint.isActive() || location.getLevel() != level) {
                continue;
            }
            double range = accessPoint.getRange();
            double dx = location.getPos().getX() - golem.getX();
            double dy = location.getPos().getY() - golem.getY();
            double dz = location.getPos().getZ() - golem.getZ();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared < range * range) {
                return true;
            }
        }
        return false;
    }

    /**
     * 以文字说明 {@link #open} 为何拒绝了该傀儡，供追踪使用。再走一遍同样的检查，
     * 而不是从 {@code open} 带出一个状态：「什么都没发生」不构成一份报告。
     */
    static String refusal(EntityThaumaturgeGolem golem, GlobalPos target) {
        if (!(golem.level() instanceof ServerLevel serverLevel)) {
            return "the golem is not on a server";
        }
        ServerLevel linkedLevel = serverLevel.getServer().getLevel(target.dimension());
        if (linkedLevel == null) {
            return "the linked dimension is not loaded";
        }
        BlockEntity blockEntity = Platform.getTickingBlockEntity(linkedLevel, target.pos());
        if (!(blockEntity instanceof IWirelessAccessPoint accessPoint)) {
            return "the block at " + target.pos() + " is not a wireless access point (it is "
                    + (blockEntity == null ? "nothing" : blockEntity.getClass().getSimpleName()) + ")";
        }
        IGrid grid = accessPoint.getGrid();
        if (grid == null) {
            return "the access point at " + target.pos() + " has no network (no channel or no power)";
        }
        if (!inRange(serverLevel, grid, golem)) {
            return "the golem at " + golem.blockPosition() + " is out of range of every active access point on that network";
        }
        return "the network is reachable";
    }
}
