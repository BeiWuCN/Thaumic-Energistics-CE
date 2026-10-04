package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;

/**
 * The Distillation Encoder's source template, set from the client. It travels although the well is a
 * slot because the item stays in the player's inventory, so vanilla's slot sync has nothing to carry.
 *
 * @param containerId the menu this applies to, so a packet for a closed screen is ignored
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
    public void handle(Player player) {
        if (player.containerMenu instanceof MenuDistillationEncoder menu && menu.containerId == containerId) {
            menu.applySourceTemplate(stack);
        }
    }
}
