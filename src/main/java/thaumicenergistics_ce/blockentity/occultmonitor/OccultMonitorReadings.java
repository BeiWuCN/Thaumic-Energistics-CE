package thaumicenergistics_ce.blockentity.occultmonitor;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor.EssentiaLine;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.util.ThELog;

/**
 * 气泡的读取面：监控器最后一次发给客户端的内容，以及发送它的快照。
 * 这里只被读，从不计算，玩家看到的气泡就是服务端的快照。
 * 书不在其中，它作为方块状态传输。
 */
final class OccultMonitorReadings {

    private final BlockEntityOccultMonitor monitor;
    private final AltarSurvey survey;
    private final EssentiaReach reach;

    private final OccultMonitorSync bubble = new OccultMonitorSync();

    OccultMonitorReadings(BlockEntityOccultMonitor monitor, AltarSurvey survey, EssentiaReach reach) {
        this.monitor = monitor;
        this.survey = survey;
        this.reach = reach;
    }

    /** 把当前读数交给同步单元；它只在有变化时才发。 */
    void sync(boolean reportable, InfusionRisk risk) {
        bubble.offer(monitor, new OccultMonitorSync.Snapshot(
                reportable,
                risk.tier(),
                risk.instability(),
                Math.round(risk.stability() * 10.0F),
                survey.report().crafting(),
                survey.craftDisplay(),
                reach.lines()));
    }

    boolean reporting() {
        return bubble.reporting();
    }

    int tier() {
        return bubble.tier();
    }

    int instability() {
        return bubble.instability();
    }

    String stability() {
        return String.format("%.1f", bubble.stabilityTimesTen() / 10.0F);
    }

    boolean crafting() {
        return bubble.crafting();
    }

    ItemStack craft() {
        return bubble.craft();
    }

    List<EssentiaLine> essentia() {
        return bubble.essentia();
    }

    void write(ValueOutput output) {
        bubble.write(output);
    }

    void apply(ValueInput input) {
        if (OccultMonitorTrace.TRACE && monitor.getLevel() != null && monitor.getLevel().isClientSide()) {
            ThELog.LOG.info(
                    "[bubble] tag at {} reporting={} tier={} instability={}",
                    monitor.getBlockPos(), input.getBooleanOr(OccultMonitorSync.TAG_REPORTING, false),
                    input.getIntOr(OccultMonitorSync.TAG_TIER, 0),
                    input.getIntOr(OccultMonitorSync.TAG_INSTABILITY, 0));
        }
        bubble.read(input);
    }
}
