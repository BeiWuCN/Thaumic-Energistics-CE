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
 * 潜行并左键点击无线奥术终端：请求服务端遗忘已配对的终端。
 * 这个点击只有客户端拥有，因为服务端最多只看到一次手臂挥动，
 * 所以该手势在这里变成数据包，而不是在那边等它出现。它只在潜行
 * 且手持终端时发送，因此普通左键点击不产生任何开销。
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
