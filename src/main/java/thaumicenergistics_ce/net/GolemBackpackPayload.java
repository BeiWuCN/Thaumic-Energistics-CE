package thaumicenergistics_ce.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * What a golem's backpack looks like, told to the players watching it: the backpack lives in the
 * golem's persistent data, which vanilla never syncs, and it is sent on a timer rather than on change.
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

    /** Hands the payload to whatever the client installed as its receiver. The protocol package names no
     * client class, and the registrar already runs handlers on the main thread - no second enqueue here. */
    public void handleOnClient(Player player) {
        ClientSinks.acceptGolemBackpack(this);
    }
}
