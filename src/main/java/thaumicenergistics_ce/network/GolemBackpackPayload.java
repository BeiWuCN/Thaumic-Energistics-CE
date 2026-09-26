package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import thaumicenergistics_ce.ThEIds;

/**
 * What a golem's backpack looks like, told to the players watching it.
 *
 * <p>The only way the client can know: a backpack lives in the golem's persistent data, which vanilla never
 * syncs. Three numbers, which is all the renderer needs.
 *
 * <p>Sent on a timer rather than on change, because the change that matters - a golem walking in or out of
 * an access point's range - is not something anyone is watching for.
 *
 * @param entityId the golem
 * @param status {@link #STATUS_NO_BACKPACK}, {@link #STATUS_IN_RANGE} or {@link #STATUS_OUT_OF_RANGE}
 * @param skinOrdinal the skin's ordinal in {@code BackpackSkins}
 */
public record GolemBackpackPayload(int entityId, int status, int skinOrdinal) implements CustomPacketPayload {

    public static final int STATUS_NO_BACKPACK = 0;
    public static final int STATUS_IN_RANGE = 1;
    public static final int STATUS_OUT_OF_RANGE = 2;

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

    /**
     * Hands the payload to the client cache.
     *
     * <p>On the client thread, as anything touching entities has to be: the payload arrives while the
     * client is mid-tick, and resolving an entity id against the level from there is the kind of thing
     * that works until it does not.
     */
    public static void handle(GolemBackpackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> thaumicenergistics_ce.client.GolemBackpackClientData.accept(payload));
    }
}
