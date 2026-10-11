package thaumicenergistics_ce.client;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;
import thaumicenergistics_ce.network.ArcaneUnbindPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class WirelessArcaneUnbindClick {

    private WirelessArcaneUnbindClick() {}

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (player.isSecondaryUseActive() && (held.getItem() instanceof ItemWirelessArcaneCraftingTerminal
                || held.getItem() instanceof thaumicenergistics_ce.item.ItemWirelessEssentiaTerminal)) {
            ClientPacketDistributor.sendToServer(ArcaneUnbindPayload.INSTANCE);
        }
    }
}
