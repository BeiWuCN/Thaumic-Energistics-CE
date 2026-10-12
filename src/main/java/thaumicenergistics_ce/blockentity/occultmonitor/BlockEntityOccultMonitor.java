package thaumicenergistics_ce.blockentity.occultmonitor;

import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.recipe.infusion.InfusionRisk;
import thaumicenergistics_ce.registry.TECEBlockEntities;

public class BlockEntityOccultMonitor extends AENetworkedBlockEntity implements IGridTickable {

    public static final int BOOK_SLOT = 0;

    private static final double IDLE_POWER = 64.0;

    static final int PULSE_TICKS = 10;

    private static final int PULSE_PARTICLES = 8;

    static final int SCAN_INTERVAL = 10;

    private final OccultMonitorBookSlot bookSlot = new OccultMonitorBookSlot(this);

    private final EssentiaReach reach = new EssentiaReach(this);

    private final AltarSurvey survey = new AltarSurvey(this, reach);

    private final OccultMonitorReadings readings = new OccultMonitorReadings(this, survey, reach);

    private long pulseUntil;

    public BlockEntityOccultMonitor(BlockPos pos, BlockState state) {
        super(TECEBlockEntities.OCCULT_MONITOR.get(), pos, state);
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
        if (!getMainNode().isActive()) {
            survey.forget();
            readings.sync(canReport(), survey.risk());
            return TickRateModulation.SAME;
        }
        survey.scan();
        readings.sync(canReport(), survey.risk());
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

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        readings.write(output);
        return output.buildResult();
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        applyBubbleState(input);
    }

    @Override
    public void onDataPacket(Connection net, ValueInput input) {
        super.onDataPacket(net, input);
        applyBubbleState(input);
    }

    private void applyBubbleState(ValueInput input) {
        readings.apply(input);
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

    public boolean hasSearchedAltar() {
        return survey.searched();
    }

    public boolean canReport() {
        return hasBook() && survey.report().foundAltar() && getMainNode().isActive();
    }

    public boolean pulsing() {
        return pulseUntil != 0L && level != null && level.getGameTime() < pulseUntil;
    }

    boolean watches(BlockPos matrix) {
        return matrix.equals(survey.matrixPos());
    }

    void startPulse() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        pulseUntil = server.getGameTime() + PULSE_TICKS;
        BlockState state = getBlockState();
        server.scheduleTick(worldPosition, state.getBlock(), PULSE_TICKS);
        server.updateNeighborsAt(worldPosition, state.getBlock());
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
    public void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        bookSlot.save(output);
        if (survey.matrixPos() != null) {
            output.putLong("MatrixPos", survey.matrixPos().asLong());
        }
    }

    @Override
    public void loadTag(ValueInput input) {
        super.loadTag(input);
        bookSlot.load(input);
        survey.setMatrixPos(input.getLong("MatrixPos").map(BlockPos::of).orElse(null));
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
    @Override
    public void preRemoveSideEffects(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        dropContents();
        super.preRemoveSideEffects(pos, state);
    }
}
