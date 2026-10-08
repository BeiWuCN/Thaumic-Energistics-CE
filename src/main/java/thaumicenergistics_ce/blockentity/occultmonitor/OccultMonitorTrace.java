package thaumicenergistics_ce.blockentity.occultmonitor;

import appeng.api.networking.IGridNode;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.util.ThELog;

/**
 * 监控器每秒一行追踪。没有它，各种失效模式（无网格、无电力、无书、无祭坛）看起来一模一样。
 * 除非 [THAUMICENERGISTICS_MONITOR_TRACE=true] 打开，否则这一行是关的，不花代价；
 * 标签命名的是打印出来的内容，不是持有它的字段名。
 */
final class OccultMonitorTrace {

    static final boolean TRACE = "true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_MONITOR_TRACE"));

    private final BlockEntityOccultMonitor monitor;
    private final AltarSurvey survey;

    private long nextTrace;

    OccultMonitorTrace(BlockEntityOccultMonitor monitor, AltarSurvey survey) {
        this.monitor = monitor;
        this.survey = survey;
    }

    void log(IGridNode node) {
        Level level = monitor.getLevel();
        if (!TRACE || level == null || level.isClientSide()) {
            return;
        }
        long now = level.getGameTime();
        if (now < nextTrace) {
            return;
        }
        nextTrace = now + 20;
        BlockEntityOccultMonitor.Report report = survey.report();
        InfusionRisk risk = survey.risk();
        ThELog.LOG.info(
                "[mon] at {} node={} book={} found={} searched={} altar={} crafting={} problems={} base={}"
                        + " altarRisk={} tier={} stability={} remaining={} instability={}",
                monitor.getBlockPos(), describeNode(node), monitor.hasBook(), report.foundAltar(),
                survey.searched(), survey.matrixPos(), report.crafting(), report.symmetryProblems(),
                risk.base(), risk.altar(), risk.tier(), String.format("%.1f", risk.stability()),
                report.remainingKinds(), risk.instability());
    }

    private String describeNode(IGridNode node) {
        if (node == null) {
            return "none";
        }
        if (node.getGrid() == null) {
            return "no-grid";
        }
        if (!node.isPowered()) {
            return "unpowered";
        }
        if (!node.hasGridBooted()) {
            return "booting";
        }
        if (!node.meetsChannelRequirements()) {
            return "no-channel";
        }
        return "active";
    }
}
