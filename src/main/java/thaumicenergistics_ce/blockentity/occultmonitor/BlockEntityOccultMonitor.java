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
 * 神秘监控器：看一座注魔祭坛，报告仪式会对房间做什么。
 * 书槽里没有魔导手册时 {@link #canReport()} 为 false；[InfusionStabilitySurvey] 指出破坏对称性的方块。
 */
public class BlockEntityOccultMonitor extends AENetworkedBlockEntity implements IGridTickable {

    public static final int BOOK_SLOT = 0;

    private static final double IDLE_POWER = 64.0;

    /** 合成完成脉冲时长；10 tick 正好半秒，闪一下干净利落。 */
    static final int PULSE_TICKS = 10;

    /** 脉冲粒子数；撒在机器上方，看得见信号从哪来。 */
    private static final int PULSE_PARTICLES = 8;

    /** 房间扫描间隔 10 tick；祭坛勘察未命中后从这个值退避。 */
    static final int SCAN_INTERVAL = 10;

    // 那本书的槽位；两个方块状态镜像它。

    private final OccultMonitorBookSlot bookSlot = new OccultMonitorBookSlot(this);

    // 读取房间分两件事：找祭坛，查什么能为仪式付账。

    private final EssentiaReach reach = new EssentiaReach(this);

    private final AltarSurvey survey = new AltarSurvey(this, reach);

    /** {@code THAUMICENERGISTICS_MONITOR_TRACE=true} 时每秒写一行日志。 */
    private final OccultMonitorTrace trace = new OccultMonitorTrace(this, survey);

    // 气泡在客户端绘制，数字走更新标签传过去。

    private final OccultMonitorReadings readings = new OccultMonitorReadings(this, survey, reach);

    // 完成仪式留下的红石脉冲；由预定的方块 tick 撤销。

    /** 脉冲结束时的游戏时间，无脉冲为 0。
     * 刻意不存档：跨过一次重载的信号会谎报早就结束的仪式。 */
    private long pulseUntil;

    public BlockEntityOccultMonitor(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OCCULT_MONITOR.get(), pos, state);
        // 要 [REQUIRE_CHANNEL]，监控器才会出现在频道读数里。
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
        // 离线时跳过工作，仍继续 tick（SAME）以察觉网格恢复。
        // 这次没搜过，上一次祭坛读数要丢弃，别当最新数据报出去。
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

    /** 气泡的客户端同步载荷；书走方块状态，不在这里。 */
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

    /** 实时更新与 {@code handleUpdateTag}（区块加载）都到这里。
     * 不走 {@code loadTag}，那个会加载物品栏。 */
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

    /** 自节点上次活跃以来是否搜过祭坛。Jade 仅在它为 true 时说 “no altar”。 */
    public boolean hasSearchedAltar() {
        return survey.searched();
    }

    public boolean canReport() {
        // 断网后留在屏幕上的气泡会报过期读数。
        return hasBook() && survey.report().foundAltar() && getMainNode().isActive();
    }

    /** 脉冲是否开着；方块把它的值当红石信号上报。 */
    public boolean pulsing() {
        return pulseUntil != 0L && level != null && level.getGameTime() < pulseUntil;
    }

    /** 本机是否观察这个 {@code matrix}，即它响应谁的合成完成。 */
    boolean watches(BlockPos matrix) {
        return matrix.equals(survey.matrixPos());
    }

    /** 开启完成仪式的脉冲，并通知周围红石。
     * 由 {@code OccultMonitorCraftPulse} 调用，不是 public；方块的预定 tick 结束它。 */
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

    /** 结束脉冲；脉冲还在时第二次仪式完成会重排 tick。 */
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
