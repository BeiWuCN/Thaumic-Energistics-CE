package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * 「把这个源质容器倒进网络」，由源质终端的右击发出。
 * 它是载荷不是菜单点击：AE2 的终端包只搬一个物品，这里要倒空一个容器：
 * 罐子空着回来，小瓶变回玻璃，这不是任何「转移槽位 N」能表达的。
 * 它只指名一个槽位，由接收方菜单重新读取，载荷不带物品堆。
 */
public record EssentiaDepositPayload(int containerId, int where) implements CustomPacketPayload {

    public static final Type<EssentiaDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "essentia_terminal_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaDepositPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaDepositPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaDepositPayload::where,
                    EssentiaDepositPayload::new);

    @Override
    public Type<EssentiaDepositPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof EssentiaTerminalReceiver receiver
                && receiver.containerId() == containerId) {
            receiver.deposit(player, where);
        }
    }
}
