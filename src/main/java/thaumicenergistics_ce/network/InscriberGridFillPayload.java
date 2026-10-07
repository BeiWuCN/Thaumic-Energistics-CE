package thaumicenergistics_ce.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;

/**
 * 知识铭刻机整个 3x3 网格，由客户端一次写入，因为逐单元
 * 更新是可见的，并会让机器把网格重复求解九遍。客户端先把同样的九个物品堆应用到
 * 自己的容器副本上，使两侧一致。{@code cells} 按阅读顺序恰好是九个物品堆，
 * 更短的列表用空物品堆补齐。
 */
public record InscriberGridFillPayload(int containerId, List<ItemStack> cells) implements CustomPacketPayload {

    public static final Type<InscriberGridFillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "inscriber_grid_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InscriberGridFillPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    InscriberGridFillPayload::containerId,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()),
                    InscriberGridFillPayload::cells,
                    InscriberGridFillPayload::new);

    @Override
    public Type<InscriberGridFillPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof KnowledgeInscriberReceiver receiver
                && receiver.containerId() == containerId) {
            receiver.applyGridFill(player, cells, receiver.gridSlotCount());
        }
    }
}
