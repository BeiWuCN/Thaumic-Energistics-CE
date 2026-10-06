package thaumicenergistics_ce.blockentity.occultmonitor;

import appeng.api.networking.IGridNode;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.util.ThELog;

/**
 * The monitor's one-second trace line: its failure modes - no grid, no power, no book, no altar - all
 * look alike without it.
 * <ul>
 *   <li>Off unless {@code THAUMICENERGISTICS_MONITOR_TRACE=true}, so the line costs nothing.
 *   <li>The labels name what is printed, not what the field that held it was called.
 * </ul>
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
