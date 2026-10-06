package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * "Put this aspect in that partition well", sent by the screen when a player drops one out of JEI.
 * A slot write cannot work: {@code FakeSlot.set} stops at the screen it ran on, and AE2's own
 * {@code InventoryActionPacket} is discarded for any menu that is not an {@code AEBaseMenu}.
 * The aspect travels as an id so the receiver can resolve it and say why a dropped one is dropped.
 */
public record PartitionWellPayload(int containerId, int well, ResourceLocation aspectId)
        implements CustomPacketPayload {

    public static final Type<PartitionWellPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "partition_well"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartitionWellPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PartitionWellPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    PartitionWellPayload::well,
                    ResourceLocation.STREAM_CODEC,
                    PartitionWellPayload::aspectId,
                    PartitionWellPayload::new);

    /** The id that means "take the mark out of the well", as on a bus: a key cannot ride the cursor. */
    public static final ResourceLocation CLEAR = ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "clear");

    @Override
    public Type<PartitionWellPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (!(player.containerMenu instanceof PartitionWellReceiver receiver)
                || receiver.containerId() != containerId) {
            return;
        }
        receiver.setPartitionWell(well, aspectId, player);
    }
}
