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

/**
 * 潜行左键点击无线奥术终端：请求服务端遗忘已配对的终端和它的 AE2 链接。
 * 服务端最多只看到一次手臂挥动，手势在这里变成数据包。
 * 只在潜行且手持终端时发送，普通左键点击没有开销。
 */
@EventBusSubscriber(modid = ThEIds.MODID, value = Dist.CLIENT)
public final class WirelessArcaneUnbindClick {

    private WirelessArcaneUnbindClick() {}

    @SubscribeEvent
    public static void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (player.isSecondaryUseActive() && held.getItem() instanceof ItemWirelessArcaneCraftingTerminal) {
            ClientPacketDistributor.sendToServer(ArcaneUnbindPayload.INSTANCE);
        }
    }
}
