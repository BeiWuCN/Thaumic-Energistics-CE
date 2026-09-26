package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;

/**
 * The Distillation Encoder's source template, set from the client.
 *
 * <p>Carries an item where {@link EncoderActionPayload} carries only numbers, because this is the one
 * instruction whose argument is not derivable on the server: everything else - the aspect list, which
 * aspect is picked, what a pattern costs - both sides compute from the source item, but the source item
 * itself can only come from the player.
 *
 * <p>It travels even though the well is a slot, because the well is a <em>template</em>: the item stays in
 * the player's inventory and is never handed over, so vanilla's slot sync - which exists to move items
 * between two real inventories - has nothing to carry. See {@code TemplateSlot}.
 *
 * @param containerId the menu this applies to, so a packet for a closed screen is ignored rather than
 *     applied to whatever the player has open now
 * @param stack the item to distil, one of it, or empty to clear the well
 */
public record EncoderSourcePayload(int containerId, ItemStack stack) implements CustomPacketPayload {

    public static final Type<EncoderSourcePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "encoder_source"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EncoderSourcePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EncoderSourcePayload::containerId,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    EncoderSourcePayload::stack,
                    EncoderSourcePayload::new);

    @Override
    public Type<EncoderSourcePayload> type() {
        return TYPE;
    }

    /** Applies the template to the open menu, if it is still this encoder's. */
    public void handle(net.minecraft.world.entity.player.Player player) {
        if (player.containerMenu instanceof MenuDistillationEncoder menu && menu.containerId == containerId) {
            menu.applySourceTemplate(stack);
        }
    }
}
