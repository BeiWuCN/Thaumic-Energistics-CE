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
 * "Forget the terminal this item was bound to", sent when the player sneaks and left-clicks with it.
 * <ul>
 *   <li>A left-click into thin air exists only on the client, so the server hears about it only here.
 *   <li>Nothing is carried: the wipe happens on the stack that is held, read again on the server rather
 *       than named by the client - a replayed packet cannot clear a stack the sender never held.
 * </ul>
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

    /**
     * Wipes the pairing on the held terminal and says so; an unbound item stays silent.
     */
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
