package thaumicenergistics_ce.client;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;
import thaumicenergistics_ce.network.ArcaneUnbindPayload;

/**
 * Sneak and left-click with the wireless arcane terminal: ask the server to forget the paired
 * terminal. The client owns this click and only the client, since the server sees an arm swing at
 * most, which is why the gesture becomes a packet here instead of being watched for there. It is
 * sent only for a sneak with the terminal in hand, so an ordinary left-click costs nothing.
 */
@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class WirelessArcaneUnbindClick {

    private WirelessArcaneUnbindClick() {}

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (player.isSecondaryUseActive() && held.getItem() instanceof ItemWirelessArcaneCraftingTerminal) {
            PacketDistributor.sendToServer(ArcaneUnbindPayload.INSTANCE);
        }
    }
}
