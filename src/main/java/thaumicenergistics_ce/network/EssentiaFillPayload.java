package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;

/**
 * 「把这个要素从网络抽进我的容器」，由源质终端的左击发出。
 * {@code aspectId} 以 id 上路：客户端拼错的键匹配不上服务端存储。
 * {@code where} 指名容器槽位，{@code stack} 只是客户端的提示。
 * {@code wholeStack} 是 shift 点击，填满手持的整个物品堆。
 */
public record EssentiaFillPayload(
        int containerId, Identifier aspectId, int where, ItemStack stack, boolean wholeStack)
        implements CustomPacketPayload {

    public static final Type<EssentiaFillPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ThEIds.MODID, "essentia_terminal_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaFillPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaFillPayload::containerId,
                    Identifier.STREAM_CODEC,
                    EssentiaFillPayload::aspectId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaFillPayload::where,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    EssentiaFillPayload::stack,
                    ByteBufCodecs.BOOL,
                    EssentiaFillPayload::wholeStack,
                    EssentiaFillPayload::new);

    @Override
    public Type<EssentiaFillPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        // 这个 record 的 stack 字段是客户端提示，这里不读。
        if (player.containerMenu instanceof EssentiaTerminalReceiver receiver
                && receiver.containerId() == containerId) {
            receiver.fillFromNetwork(player, where, aspectId, wholeStack);
        }
    }
}
