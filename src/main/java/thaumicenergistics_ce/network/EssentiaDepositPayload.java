package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

public record EssentiaDepositPayload(int containerId, int where) implements CustomPacketPayload {

    public static final Type<EssentiaDepositPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ThEIds.MODID, "essentia_terminal_deposit"));

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
