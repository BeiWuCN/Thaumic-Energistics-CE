package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

public record PartitionWellPayload(int containerId, int well, Identifier aspectId)
        implements CustomPacketPayload {

    public static final Type<PartitionWellPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ThEIds.MODID, "partition_well"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartitionWellPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PartitionWellPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    PartitionWellPayload::well,
                    Identifier.STREAM_CODEC,
                    PartitionWellPayload::aspectId,
                    PartitionWellPayload::new);

    public static final Identifier CLEAR = Identifier.fromNamespaceAndPath(ThEIds.MODID, "clear");

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
