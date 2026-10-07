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
 * 「忘记这件物品绑定的终端」，玩家潜行并左键点击它时发出。
 * 朝空气中左键只存在于客户端，所以服务端只能从这里得知。
 * 载荷不携带任何内容：清除作用在手持的那个物品堆上，由服务端重新读取而不是由客户端
 * 指名，因此重放的包无法清除发送者
 * 从未持有过的物品堆。
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

    /** 清除手持终端上的配对并加以告知；未绑定的物品保持沉默。 */
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
