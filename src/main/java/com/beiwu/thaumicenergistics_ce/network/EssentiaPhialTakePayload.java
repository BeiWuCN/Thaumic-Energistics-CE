package com.beiwu.thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import com.beiwu.thaumicenergistics_ce.ThEIds;

/**
 * 「空手点这一行，给我一瓶装满的」，由源质终端的左击发出。
 * <p>和 AE2 流体终端的桶同一条路：光标空着时，终端先从网络抽一个空容器，
 * 再把它灌满。这里抽的是空瓶，灌的是一整瓶源质。
 * <p>{@code aspectId} 以 id 上路：客户端拼错的键匹配不上服务端存储，
 * 也不会误扣别的源质。真正的门槛（够不够一瓶、有没有空瓶）由服务端再判一次。
 */
public record EssentiaPhialTakePayload(int containerId, ResourceLocation aspectId)
        implements CustomPacketPayload {

    public static final Type<EssentiaPhialTakePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "essentia_terminal_take_phial"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaPhialTakePayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaPhialTakePayload::containerId,
                    ResourceLocation.STREAM_CODEC,
                    EssentiaPhialTakePayload::aspectId,
                    EssentiaPhialTakePayload::new);

    @Override
    public Type<EssentiaPhialTakePayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof EssentiaTerminalReceiver receiver
                && receiver.containerId() == containerId) {
            receiver.takeFilledPhial(player, aspectId);
        }
    }
}
