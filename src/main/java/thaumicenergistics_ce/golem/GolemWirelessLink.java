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
 * One golem's resolved route into an ME network: a link to an access point, thrown away after use.
 * <ul>
 * <li>A backpack holds a {@link GlobalPos} and nothing else, so the block there must be an access point
 * with a grid in range of the golem; resolved per operation, so walking away is noticed at once.
 * <li>Only items move, and a network that cannot pay moves nothing: AE2's powered helpers decide.
 * </ul>
 */
public final class GolemWirelessLink {

    /** Items per operation, by golem rank. The reference build's own numbers, selected by rank. */
    private static final int[] ITEM_RATES = {8, 24, 32};

    private final MEStorage storage;
    private final IEnergyService energy;
    private final IActionSource source;

    private GolemWirelessLink(MEStorage storage, IEnergyService energy, IActionSource source) {
        this.storage = storage;
        this.energy = energy;
        this.source = source;
    }

    /** The network behind this golem's link, or null if it cannot be reached from where the golem is. */
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

        // The access point is billed for the transfer, and is an action host, which AE2's security and
        // channel accounting expect for anything acting on a grid's behalf.
        return new GolemWirelessLink(
                grid.getStorageService().getInventory(),
                grid.getEnergyService(),
                IActionSource.ofMachine(accessPoint));
    }

    /**
     * Puts as much of a stack into the network as it will take, and shrinks the stack by that much. Safe
     * because {@code getCarrying()} hands out the golem's own live stacks, so the caller is shrunk in place.
     *
     * @return how many items were accepted, which is zero for a network that is full or out of power.
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

    /** Items per operation for this golem. */
    public static int itemRate(EntityThaumaturgeGolem golem) {
        int rank = Math.max(0, Math.min(ITEM_RATES.length - 1, golem.getProperties().getRank()));
        return ITEM_RATES[rank];
    }

    /**
     * Whether the golem is in range of an active access point on the grid it is linked to. The class
     * asked for has to be the concrete one - see the note on {@code owner.getClass()}.
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
     * Why {@link #open} refused this golem, in words, for the trace. A second pass over the same checks
     * rather than a status carried out of {@code open}: "nothing happened" is not a report.
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
