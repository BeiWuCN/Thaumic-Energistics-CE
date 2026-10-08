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
 * 蒸馏编码器的源模板，由客户端设置。
 * 它对应一个槽位却仍要上路：物品留在玩家物品栏里，原版槽位同步没有东西可送。
 * @param containerId 它作用的菜单，发给已关闭屏幕的数据包被忽略
 * @param stack 要蒸馏的物品，数量为一，空则清空该槽位
 */
public record EncoderSourcePayload(int containerId, ItemStack stack) implements CustomPacketPayload {

    public static final Type<EncoderSourcePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ThEIds.MODID, "encoder_source"));

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

    public void handle(Player player) {
        if (player.containerMenu instanceof DistillationEncoderReceiver receiver
                && receiver.containerId() == containerId) {
            receiver.applySourceTemplate(stack);
        }
    }
}
