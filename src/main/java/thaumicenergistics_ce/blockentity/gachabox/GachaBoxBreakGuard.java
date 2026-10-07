package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.block.BlockGachaBox;

/**
 * 让箱子里的脑跟着它绑定的那个玩家：否则破坏箱子会把脑掉出来
 * 任何人都能捡。创造模式不受限制，这样操作员清理机器时
 * 不必去敲命令。
 */
@EventBusSubscriber(modid = ThEIds.MODID)
public final class GachaBoxBreakGuard {

    private GachaBoxBreakGuard() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof BlockGachaBox)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isCreative()) {
            return;
        }
        if (event.getLevel().getBlockEntity(event.getPos()) instanceof BlockEntityGachaBox box
                && box.hasJar()
                && !box.mayTakeBrain(player)) {
            event.setCanceled(true);
            player.displayClientMessage(
                    Component.translatable(
                            "block.thaumicenergistics_ce.gacha_box.bound_to_other",
                            box.ownerName() == null ? "" : box.ownerName()),
                    true);
        }
    }
}
