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
 * 知识铭刻机合成网格的一格，由客户端写。网格是幽灵的，槽位不可能是真槽，
 * 只有槽位和物品堆上路，网格意味着什么由服务端定。
 * @param containerSlot 容器索引，不是网格索引；偏移量归接收者
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
        // 载荷带的是容器索引不是网格位置；收到时换算才让两者不漂。当成同一样，每个格子都会被当越界丢掉。
        int cell = containerSlot - receiver.gridSlotStart();
        if (cell < 0 || cell >= receiver.gridSlotCount()) {
            return;
        }
        receiver.setGridCell(player, cell, stack);
    }
}
