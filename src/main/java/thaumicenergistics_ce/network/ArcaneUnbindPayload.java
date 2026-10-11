package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.item.ItemWirelessArcaneCraftingTerminal;

public record ArcaneUnbindPayload() implements CustomPacketPayload {

    public static final ArcaneUnbindPayload INSTANCE = new ArcaneUnbindPayload();

    public static final Type<ArcaneUnbindPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ThEIds.MODID, "arcane_terminal_unbind"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneUnbindPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<ArcaneUnbindPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof ItemWirelessArcaneCraftingTerminal
                && ItemWirelessArcaneCraftingTerminal.unbind(held)) {
            player.sendOverlayMessage(
                    Component.translatable(
                            "item.thaumicenergistics_ce.wireless_arcane_crafting_terminal.cleared"));
        }
    }
}
