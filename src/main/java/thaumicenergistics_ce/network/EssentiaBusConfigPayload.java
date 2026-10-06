package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * "Put this aspect in that config slot", sent by a bus screen when a player drops one out of JEI.
 * A slot write cannot work, because {@code ConfigMenuInventory} converts through {@code AEItemKey},
 * so a non-item key is dropped and the mark vanishes when the server answers. The aspect therefore
 * travels as an id, and an empty {@link ResourceLocation} clears the slot.
 */
public record EssentiaBusConfigPayload(int containerId, int configSlot, ResourceLocation aspectId)
        implements CustomPacketPayload {

    public static final Type<EssentiaBusConfigPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "essentia_bus_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaBusConfigPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaBusConfigPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaBusConfigPayload::configSlot,
                    ResourceLocation.STREAM_CODEC,
                    EssentiaBusConfigPayload::aspectId,
                    EssentiaBusConfigPayload::new);

    public static final ResourceLocation CLEAR = ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "clear");

    @Override
    public Type<EssentiaBusConfigPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (!(player.containerMenu instanceof EssentiaBusReceiver receiver)
                || receiver.containerId() != containerId) {
            return;
        }
        // The aspect is resolved and checked on the receiver side, which is where a dropped one can say why.
        receiver.setConfigAspect(configSlot, aspectId, player);
    }
}
