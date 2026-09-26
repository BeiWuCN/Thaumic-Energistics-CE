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
 * One golem's way into an ME network, resolved on use and thrown away afterwards.
 *
 * <p>A backpack holds a {@link GlobalPos} and nothing else, so reaching the network means resolving that
 * position: the block at the link has to be a wireless access point with a grid, and the golem has to be
 * within range of an active access point on that grid. Resolved per operation rather than cached, because a
 * golem walks and a cached grid would keep working after it had wandered out of range.
 *
 * <p>Only items move. A Thaumaturge golem has no hold for fluids or essentia, so the reference build's
 * fluid and essentia transfers would have nothing to travel through. Energy is not checked here either:
 * every transfer goes through AE2's powered helpers, so a network that cannot pay moves nothing.
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

        // The access point is the machine the transfer is billed to, and it is an action host, which is
        // what AE2's security and channel accounting expect for anything acting on a grid's behalf.
        return new GolemWirelessLink(
                grid.getStorageService().getInventory(),
                grid.getEnergyService(),
                IActionSource.ofMachine(accessPoint));
    }

    /**
     * Puts as much of a stack into the network as it will take, and shrinks the stack by that much.
     *
     * <p>Shrinking the caller's stack is safe because the stack is the golem's own held item, not a copy:
     * {@code getCarrying()} hands out the live stacks. That is also why this takes a stack rather than
     * returning one - a golem's hand is the one place the transfer's two halves have to meet, and a copy
     * would leave the golem holding what the network had already been given.
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
     * Whether the golem is in range of an active access point on the grid it is linked to.
     *
     * <p>Range belongs to the access point, not to the link: a network can have several, and a golem is
     * connected if any one of them reaches it. The linked access point is the one that gets billed.
     *
     * <p><b>The class asked for here has to be the concrete one.</b> A grid's machine map is keyed by
     * {@code owner.getClass()} - the exact class of the block entity - so
     * {@code getMachines(IWirelessAccessPoint.class)} returns an empty set on every network there is, and a
     * range check built on it says "out of range" for a golem standing on top of the access point. AE2's own
     * wireless terminal asks for {@code WirelessAccessPointBlockEntity.class} for exactly this reason, and
     * this does the same. Whether the block entity <em>is</em> an access point is a different question,
     * asked with {@code instanceof} when the link is resolved.
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
     * Why {@link #open} refused this golem, in words, for the trace.
     *
     * <p>Written as a second pass over the same checks rather than as a status carried out of {@code open},
     * because {@code open} runs twice a second per golem and the words are only ever wanted when someone is
     * reading a log. "Nothing happened" is not a report; "the block at the link is not a wireless access
     * point" is.
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
