package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;

/**
 * 「忘记这件物品绑的终端」，玩家潜行左键点它时发出。对空气左键只存在于客户端，
 * 服务端只能从这里知道。载荷不带东西：清除作用在手持的那个物品堆上，
 * 由服务端重读不是客户端指名，重放的包清不掉发送者从没拿过的堆。
 */
public record ArcaneUnbindPayload() implements CustomPacketPayload {

    public static final ArcaneUnbindPayload INSTANCE = new ArcaneUnbindPayload();

    public static final Type<ArcaneUnbindPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "arcane_terminal_unbind"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneUnbindPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<ArcaneUnbindPayload> type() {
        return TYPE;
    }

    /** 清掉手持终端上的配对并告知；没绑定的物品不出声。 */
    public void handle(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof ItemWirelessArcaneCraftingTerminal
                && ItemWirelessArcaneCraftingTerminal.unbind(held)) {
            player.displayClientMessage(
                    Component.translatable(
                            "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal.cleared"),
                    true);
        }
    }
}
