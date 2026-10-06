package thaumicenergistics_ce.blockentity.occultmonitor;

import com.leclowndu93150.thaumaturge.api.infusion.InfusionCraftedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import thaumicenergistics_ce.ThEIds;

/**
 * What a finished ritual does to the machines that watch it: one redstone pulse each. Thaumaturge
 * announces the finish on the game bus from finishCraft, never from failCraft, because a ruined
 * ritual raises nothing and the pulse keeps its meaning; only the machines already watching that
 * altar answer, and they are found by scanning for it.
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class OccultMonitorCraftPulse {

    private OccultMonitorCraftPulse() {}

    @SubscribeEvent
    public static void onCrafted(InfusionCraftedEvent event) {
        ServerLevel level = event.getLevel();
        BlockPos matrix = event.getMatrixPos();
        int range = AltarSurvey.ALTAR_SCAN_RANGE;
        for (BlockPos pos : BlockPos.betweenClosed(
                matrix.offset(-range, -range, -range), matrix.offset(range, range, range))) {
            if (level.getBlockEntity(pos) instanceof BlockEntityOccultMonitor monitor
                    && monitor.watches(matrix)) {
                monitor.startPulse();
            }
        }
    }
}
