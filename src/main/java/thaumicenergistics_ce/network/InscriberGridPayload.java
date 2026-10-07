package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;

/**
 * 知识铭刻机合成网格的一个单元，由客户端写入。网格是幽灵的，所以
 * 槽位不可能是真实槽位，只有槽位与物品堆上路——网格意味着什么由服务端决定。
 * @param containerSlot 容器索引，不是网格索引；偏移量由接收者掌管
 */
public record InscriberGridPayload(int containerId, int containerSlot, ItemStack stack) implements CustomPacketPayload {

    public static final Type<InscriberGridPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "inscriber_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InscriberGridPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    InscriberGridPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    InscriberGridPayload::containerSlot,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    InscriberGridPayload::stack,
                    InscriberGridPayload::new);

    @Override
    public Type<InscriberGridPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (!(player.containerMenu instanceof KnowledgeInscriberReceiver receiver)
                || receiver.containerId() != containerId) {
            return;
        }
        // 载荷携带的是容器索引而非网格位置；在收到时换算正是
        // 防止两者漂移的办法——把其一当成其二会把每个单元都当作越界丢弃。
        int cell = containerSlot - receiver.gridSlotStart();
        if (cell < 0 || cell >= receiver.gridSlotCount()) {
            return;
        }
        receiver.setGridCell(player, cell, stack);
    }
}
