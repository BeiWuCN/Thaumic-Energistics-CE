package thaumicenergistics_ce.blockentity.occultmonitor;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.init.ModBlockEntities;

/**
 * The Occult Monitor: watches an Infusion Altar and reports what the ritual will do to the room.
 * <ul>
 *   <li>{@code InfusionStabilitySurvey} names the blocks that break the altar's symmetry.
 *   <li>A Thaumonomicon must be in the book slot, or {@link #canReport()} stays false.
 * </ul>
 */
public class BlockEntityOccultMonitor extends AENetworkedBlockEntity implements IGridTickable {

    public static final int BOOK_SLOT = 0;

    private static final double IDLE_POWER = 32.0;

    /** How long the finished-craft pulse stands, in game ticks. Half a second is one clean flash. */
    static final int PULSE_TICKS = 10;

    /** The dust the pulse leaves over the machine, so a player sees where the signal came from. */
    private static final int PULSE_PARTICLES = 8;

    /** How often the room is looked at. The altar survey backs off from this value after a miss. */
    static final int SCAN_INTERVAL = 10;

    // The book, the two blockstates that mirror it, and the right-click it answers, in one place.

    private final OccultMonitorBookSlot bookSlot = new OccultMonitorBookSlot(this);

    // Reading the room is two jobs: finding the altar, and asking what can pay for the ritual.

    private final EssentiaReach reach = new EssentiaReach(this);

    private final AltarSurvey survey = new AltarSurvey(this, reach);

    /** One log line a second when {@code THAUMICENERGISTICS_MONITOR_TRACE=true}, because the failure
     * modes - no grid, no power, no book, no altar - otherwise look alike. */
    private final OccultMonitorTrace trace = new OccultMonitorTrace(this, survey);

    // The bubble is drawn on the client, so its numbers travel in the update tag.

    private final OccultMonitorReadings readings = new OccultMonitorReadings(this, survey, reach);

    // What a finished ritual leaves behind: one redstone pulse, taken down by a scheduled block tick.

    /** The game time the pulse ends at, or zero when none is running. Not saved: a signal that outlived
     * its ritual - across a reload, say - would be a lie about an altar that is long done. */
    private long pulseUntil;

    public BlockEntityOccultMonitor(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OCCULT_MONITOR.get(), pos, state);
        // REQUIRE_CHANNEL so the monitor shows up in channel readings, as every other machine does.
        getMainNode()
                .setIdlePowerUsage(IDLE_POWER)
                .addService(IGridTickable.class, this)
                .setFlags(GridFlags.REQUIRE_CHANNEL);
    }

    public SimpleContainer getInventory() {
        return bookSlot.container();
    }

    public ItemStack getBook() {
        return bookSlot.book();
    }

    public boolean hasBook() {
        return bookSlot.has();
    }

    /** Adds the book, or removes it only when the player sneaks - a plain right-click would disarm
     * the machine. See {@code BlockOccultMonitor}. */
    public @Nullable ItemStack interact(ItemStack held, boolean sneaking) {
        return bookSlot.interact(held, sneaking);
    }

    public void updateNetworkState() {
        bookSlot.updateNetworkState();
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(SCAN_INTERVAL, SCAN_INTERVAL, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLast) {
        if (level == null || level.isClientSide()) {
            return TickRateModulation.IDLE;
        }
        updateNetworkState();
        // Offline: skip work, but keep ticking (SAME) so the grid's return is noticed. Nothing was
        // searched, so the last altar reading is dropped rather than served as if it were current.
        if (!getMainNode().isActive()) {
            survey.forget();
            readings.sync(canReport(), survey.risk());
            trace.log(node);
            return TickRateModulation.SAME;
        }
        survey.scan();
        readings.sync(canReport(), survey.risk());
        trace.log(node);
        return TickRateModulation.SAME;
    }

    public InfusionRisk risk() {
        return survey.risk();
    }

    public boolean bubbleReporting() {
        return readings.reporting();
    }

    public int bubbleTier() {
        return readings.tier();
    }

    public int bubbleInstability() {
        return readings.instability();
    }

    public String bubbleStability() {
        return readings.stability();
    }

    public boolean bubbleCrafting() {
        return readings.crafting();
    }

    public ItemStack bubbleCraft() {
        return readings.craft();
    }

    public List<EssentiaLine> bubbleEssentia() {
        return readings.essentia();
    }

    /** Client sync payload for the bubble. The book is not here - it travels as a blockstate. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        readings.write(tag, registries);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        applyBubbleState(tag, registries);
    }

    /** A live update arrives here; {@code handleUpdateTag} is the chunk-load route. Both end here,
     * not in {@code loadTag}, which would load the inventory. */
    @Override
    public void onDataPacket(
            Connection net,
            ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        applyBubbleState(packet.getTag(), registries);
    }

    private void applyBubbleState(CompoundTag tag, HolderLookup.Provider registries) {
        readings.apply(tag, registries);
    }

    public record EssentiaLine(String aspect, int drawn, int total) {
        @Override
        public String toString() {
            return aspect + "=" + drawn + "/" + total;
        }
    }

    public Report report() {
        return survey.report();
    }

    /** Whether an altar search has run since the node was last active. Jade says "no altar" only when
     * this is true, since an unsearched machine knows nothing about the room. */
    public boolean hasSearchedAltar() {
        return survey.searched();
    }

    public boolean canReport() {
        // Online too: a bubble left on screen after the network went down would report a stale reading.
        return hasBook() && survey.report().foundAltar() && getMainNode().isActive();
    }

    /** Whether the finished-craft pulse is up, which is what the block reports as its signal. */
    public boolean pulsing() {
        return pulseUntil != 0L && level != null && level.getGameTime() < pulseUntil;
    }

    /** Whether {@code matrix} is the altar this machine watches, and so whose finished craft it answers. */
    boolean watches(BlockPos matrix) {
        return matrix.equals(survey.matrixPos());
    }

    /** Starts the pulse a completed ritual earns, and tells the redstone around the machine. Called by
     * {@code OccultMonitorCraftPulse}, which is why it is not public; the block's scheduled tick ends it. */
    void startPulse() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        pulseUntil = server.getGameTime() + PULSE_TICKS;
        BlockState state = getBlockState();
        server.scheduleTick(worldPosition, state.getBlock(), PULSE_TICKS);
        server.updateNeighborsAt(worldPosition, state.getBlock());
        // A few particles over the block, so the pulse is seen where it comes from and not just felt.
        server.sendParticles(
                DustParticleOptions.REDSTONE,
                worldPosition.getX() + 0.5,
                worldPosition.getY() + 1.05,
                worldPosition.getZ() + 0.5,
                PULSE_PARTICLES,
                0.4,
                0.05,
                0.4,
                0.0);
    }

    /** Ends the pulse, or re-arms the tick when a second ritual finished while this one was still up. */
    public void endPulse() {
        if (pulseUntil == 0L || level == null) {
            return;
        }
        long now = level.getGameTime();
        if (now < pulseUntil) {
            level.scheduleTick(worldPosition, getBlockState().getBlock(), (int) (pulseUntil - now));
            return;
        }
        pulseUntil = 0L;
        level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        bookSlot.save(tag, registries);
        if (survey.matrixPos() != null) {
            tag.putLong("MatrixPos", survey.matrixPos().asLong());
        }
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        bookSlot.load(tag, registries);
        survey.setMatrixPos(tag.contains("MatrixPos") ? BlockPos.of(tag.getLong("MatrixPos")) : null);
    }

    public void dropContents() {
        bookSlot.drop();
    }

    public record Report(
            boolean foundAltar,
            boolean crafting,
            float stability,
            AspectList remaining,
            List<BlockPos> problemBlocks) {

        public static final Report NONE = new Report(false, false, 0.0F, AspectList.EMPTY, List.of());

        public int symmetryProblems() {
            return problemBlocks.size();
        }

        public int remainingKinds() {
            return remaining == null ? 0 : remaining.size();
        }
    }
}
