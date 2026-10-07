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
 * 神秘监控器：观察一座注魔祭坛，并报告仪式将对房间做什么。
 * [InfusionStabilitySurvey] 指明破坏祭坛对称性的方块，而且书槽里必须有一本
 * 魔导手册，否则 {@link #canReport()} 保持为 false。
 */
public class BlockEntityOccultMonitor extends AENetworkedBlockEntity implements IGridTickable {

    public static final int BOOK_SLOT = 0;

    private static final double IDLE_POWER = 64.0;

    /** 合成完成脉冲持续多久，单位为游戏 tick。半秒恰好是一次干净的闪烁。 */
    static final int PULSE_TICKS = 10;

    /** 脉冲在机器上方留下的粒子，让玩家看见信号是从哪来的。 */
    private static final int PULSE_PARTICLES = 8;

    /** 多久查看一次房间。祭坛勘察未命中后会从这个值退避。 */
    static final int SCAN_INTERVAL = 10;

    // 那本典籍的槽位，以及镜像它的两个方块状态。

    private final OccultMonitorBookSlot bookSlot = new OccultMonitorBookSlot(this);

    // 读取房间分两件事：找到祭坛，以及查问什么能为这场仪式付账。

    private final EssentiaReach reach = new EssentiaReach(this);

    private final AltarSurvey survey = new AltarSurvey(this, reach);

    /** 当 {@code THAUMICENERGISTICS_MONITOR_TRACE=true} 时每秒一行日志。 */
    private final OccultMonitorTrace trace = new OccultMonitorTrace(this, survey);

    // 气泡在客户端绘制，所以它的数字随更新标签一起传输。

    private final OccultMonitorReadings readings = new OccultMonitorReadings(this, survey, reach);

    // 一次完成的仪式留下的东西：一次红石脉冲，由预定的方块 tick 撤销。

    /** 脉冲结束时的游戏时间，没有脉冲运行时为 0。不保存：一个比仪式活得更久的信号——
     * 比如跨过一次重载——就是对早已结束的祭坛的谎报。 */
    private long pulseUntil;

    public BlockEntityOccultMonitor(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OCCULT_MONITOR.get(), pos, state);
        // [REQUIRE_CHANNEL]，好让监控器像其它所有机器一样出现在频道读数里。
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
        // 离线：跳过工作，但仍继续 tick（SAME），以便察觉网格恢复。没有做过搜索，
        // 所以丢弃上一次祭坛读数，而不是把它当作最新数据提供出去。
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

    /** 气泡的客户端同步载荷。书不在这里——它作为方块状态传输。 */
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

    /** 实时更新从这里进来；{@code handleUpdateTag} 是区块加载路径。两者都终止于此，
     * 而不是 {@code loadTag}，后者会加载物品栏。 */
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

    /** 自节点上次活跃以来是否跑过祭坛搜索。只有它为 true 时 Jade 才说“no altar”，
     * 因为未搜索过的机器对房间一无所知。 */
    public boolean hasSearchedAltar() {
        return survey.searched();
    }

    public boolean canReport() {
        // 在线时也一样：网络断开后留在屏幕上的气泡会报告过期的读数。
        return hasBook() && survey.report().foundAltar() && getMainNode().isActive();
    }

    /** 合成完成脉冲是否处于开启状态，方块正是把它作为信号上报的。 */
    public boolean pulsing() {
        return pulseUntil != 0L && level != null && level.getGameTime() < pulseUntil;
    }

    /** {@code matrix} 是否是本机器观察的祭坛，也就是它响应谁的合成完成。 */
    boolean watches(BlockPos matrix) {
        return matrix.equals(survey.matrixPos());
    }

    /** 开启一次完成的仪式所挣得的脉冲，并通知机器周围的红石。由
     * {@code OccultMonitorCraftPulse} 调用，所以它不是 public；方块的预定 tick 会结束它。 */
    void startPulse() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        pulseUntil = server.getGameTime() + PULSE_TICKS;
        BlockState state = getBlockState();
        server.scheduleTick(worldPosition, state.getBlock(), PULSE_TICKS);
        server.updateNeighborsAt(worldPosition, state.getBlock());
        // 方块上方撒几个粒子，让脉冲不只是被感觉到，而是看得见它从哪来。
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

    /** 结束脉冲；若在本次脉冲仍在时第二次仪式完成，则重新安排 tick。 */
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
