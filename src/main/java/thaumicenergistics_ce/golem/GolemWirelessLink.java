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

public final class GolemWirelessLink {

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

        return new GolemWirelessLink(
                grid.getStorageService().getInventory(),
                grid.getEnergyService(),
                IActionSource.ofMachine(accessPoint));
    }

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
        int rank = Math.max(0, Math.min(ITEM_RATES.length - 1, golem.properties().rank()));
        return ITEM_RATES[rank];
    }

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
