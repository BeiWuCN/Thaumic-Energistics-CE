package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import thaumicenergistics_ce.ThEIds;

public record GolemBackpackPayload(int entityId, int status, int skinOrdinal) implements CustomPacketPayload {

    public static final int STATUS_NO_BACKPACK = 0;
    public static final int STATUS_IN_RANGE = 1;
    public static final int STATUS_OUT_OF_RANGE = 2;
    public static final int STATUS_WORKING = 3;

    public static final CustomPacketPayload.Type<GolemBackpackPayload> TYPE =
            new CustomPacketPayload.Type<>(ThEIds.id("golem_backpack"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GolemBackpackPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(payload.entityId);
                buffer.writeVarInt(payload.status);
                buffer.writeVarInt(payload.skinOrdinal);
            },
            buffer -> new GolemBackpackPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));

    @Override
    public CustomPacketPayload.Type<GolemBackpackPayload> type() {
        return TYPE;
    }
}
