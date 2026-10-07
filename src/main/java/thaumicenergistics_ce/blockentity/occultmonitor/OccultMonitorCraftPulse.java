package thaumicenergistics_ce.blockentity.occultmonitor;

import com.leclowndu93150.thaumaturge.api.infusion.InfusionCraftedEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import thaumicenergistics_ce.ThEIds;

/**
 * 完成的仪式对观察它的机器做什么：各给一次红石脉冲。Thaumaturge 从 [finishCraft]
 * 在游戏总线上宣告完成，绝不从 [failCraft]，因为失败的仪式不会触发任何东西，
 * 脉冲才得以保持其含义；只有已经在观察那座祭坛的机器会响应，它们靠扫描
 * 祭坛找出。
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
