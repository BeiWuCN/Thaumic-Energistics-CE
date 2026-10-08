package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import thaumicenergistics_ce.ThEIds;

/**
 * 傀儡背包长什么样，告知正在看它的玩家。
 * 背包存在傀儡的持久数据里，原版从不同步，按定时器发送而非随变化发送。
 * @param entityId 该傀儡
 * @param status {@link #STATUS_NO_BACKPACK}、{@link #STATUS_IN_RANGE} 或 {@link #STATUS_OUT_OF_RANGE}
 * @param skinOrdinal 该皮肤在 {@code BackpackSkins} 中的序号
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
}
