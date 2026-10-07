package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.block.BlockGachaBox;

/**
 * Keeps the box's brain with the player it was bound to: breaking the box would otherwise drop the
 * brain for anyone to pick up. Creative is left alone, so an operator clearing a machine does not
 * have to reach for a command.
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
