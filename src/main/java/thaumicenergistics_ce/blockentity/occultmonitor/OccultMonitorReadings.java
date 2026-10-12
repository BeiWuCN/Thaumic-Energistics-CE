package thaumicenergistics_ce.blockentity.occultmonitor;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor.EssentiaLine;
import thaumicenergistics_ce.recipe.infusion.InfusionRisk;

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

        bubble.read(input);
    }
}
