package thaumicenergistics_ce.blockentity.occultmonitor;

import com.leclowndu93150.thaumaturge.api.infusion.InfusionCraftedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import thaumicenergistics_ce.ThEIds;

/**
 * 完成的仪式对看着它的机器做什么：各给一次红石脉冲。Thaumaturge 从 [finishCraft]
 * 在游戏总线上宣告完成，绝不从 [failCraft]：失败的仪式什么都不触发，脉冲的含义才守得住；
 * 只有已在看那座祭坛的机器会响应，它们靠扫描祭坛找出。
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
