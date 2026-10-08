package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * “把这个要素放进那个配置槽位”，玩家从 JEI 里拖一个出来时由总线界面发出。
 * <ul>
 *   <li>直接写槽位不行：{@code ConfigMenuInventory} 会经 {@code AEItemKey} 转换，
 *       非物品的键被丢掉，服务端一应答标记就没了。
 *   <li>所以要素以 id 传输；空的 {@link Identifier} 清空槽位。
 * </ul>
 */
public record EssentiaBusConfigPayload(int containerId, int configSlot, Identifier aspectId)
        implements CustomPacketPayload {

    public static final Type<EssentiaBusConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ThEIds.MODID, "essentia_bus_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaBusConfigPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaBusConfigPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaBusConfigPayload::configSlot,
                    Identifier.STREAM_CODEC,
                    EssentiaBusConfigPayload::aspectId,
                    EssentiaBusConfigPayload::new);

    public static final Identifier CLEAR = Identifier.fromNamespaceAndPath(ThEIds.MODID, "clear");

    @Override
    public Type<EssentiaBusConfigPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (!(player.containerMenu instanceof EssentiaBusReceiver receiver)
                || receiver.containerId() != containerId) {
            return;
        }
        // 要素在接收端解析和检查，被丢掉的也只有在那里才能说明原因。
        receiver.setConfigAspect(configSlot, aspectId, player);
    }
}
