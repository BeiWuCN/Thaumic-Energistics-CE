package thaumicenergistics_ce.blockentity;

import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionMonitor.EssentiaLine;
import thaumicenergistics_ce.infusion.InfusionRisk;
import thaumicenergistics_ce.util.ThELog;

/**
 * The bubble's read face: what the monitor last sent to a client, and the snapshot that sends it.
 * <ul>
 *   <li>Read through, never computed here: the bubble the player sees is the server's snapshot.
 *   <li>The book is not part of it - it travels as a blockstate.
 * </ul>
 */
final class InfusionMonitorReadings {

    private final BlockEntityInfusionMonitor monitor;
    private final AltarSurvey survey;
    private final EssentiaReach reach;

    private final InfusionMonitorSync bubble = new InfusionMonitorSync();

    InfusionMonitorReadings(BlockEntityInfusionMonitor monitor, AltarSurvey survey, EssentiaReach reach) {
        this.monitor = monitor;
        this.survey = survey;
        this.reach = reach;
    }

    /** Offers the current reading to the sync unit, which sends it only when something moved. */
    void sync(boolean reportable, InfusionRisk risk) {
        bubble.offer(monitor, new InfusionMonitorSync.Snapshot(
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

    void write(CompoundTag tag, HolderLookup.Provider registries) {
        bubble.write(tag, registries);
    }

    void apply(CompoundTag tag, HolderLookup.Provider registries) {
        if (MonitorTrace.TRACE && monitor.getLevel() != null && monitor.getLevel().isClientSide()) {
            ThELog.LOG.info(
                    "[bubble] tag at {} reporting={} tier={} instability={}",
                    monitor.getBlockPos(), tag.getBoolean(InfusionMonitorSync.TAG_REPORTING),
                    tag.getInt(InfusionMonitorSync.TAG_TIER),
                    tag.getInt(InfusionMonitorSync.TAG_INSTABILITY));
        }
        bubble.read(tag, registries);
    }
}
