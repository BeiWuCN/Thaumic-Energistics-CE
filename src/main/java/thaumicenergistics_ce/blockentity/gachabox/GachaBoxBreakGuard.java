package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.block.BlockGachaBox;

@EventBusSubscriber(modid = ThEIds.MODID)
public final class GachaBoxBreakGuard {

    private GachaBoxBreakGuard() {}

    @SubscribeEvent
    public static void onBreak(BreakBlockEvent event) {
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
            player.sendOverlayMessage(
                    Component.translatable(
                            "block.thaumicenergistics_ce.gacha_box.bound_to_other",
                            box.ownerName() == null ? "" : box.ownerName()));
        }
    }
}
