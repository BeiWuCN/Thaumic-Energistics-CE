package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * 「把这个要素放进那个分区槽位」，玩家从 JEI 拖出一个时由屏幕发出。
 * 槽位写入行不通：{@code FakeSlot.set} 止步于它所在的那个屏幕。
 * AE2 的 {@code InventoryActionPacket} 会丢弃任何不是 {@code AEBaseMenu} 的菜单。
 * 要素以 id 上路，接收者才解析得出是哪一个，以及它为何被丢弃。
 */
public record PartitionWellPayload(int containerId, int well, ResourceLocation aspectId)
        implements CustomPacketPayload {

    public static final Type<PartitionWellPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "partition_well"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PartitionWellPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    PartitionWellPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    PartitionWellPayload::well,
                    ResourceLocation.STREAM_CODEC,
                    PartitionWellPayload::aspectId,
                    PartitionWellPayload::new);

    /** 表示「把标记从槽位取出」的 id，与总线上一致：键没法搭在光标上。 */
    public static final ResourceLocation CLEAR = ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "clear");

    @Override
    public Type<PartitionWellPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (!(player.containerMenu instanceof PartitionWellReceiver receiver)
                || receiver.containerId() != containerId) {
            return;
        }
        receiver.setPartitionWell(well, aspectId, player);
    }
}
