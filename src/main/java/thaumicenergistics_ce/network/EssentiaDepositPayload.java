package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.ThEIds;

/**
 * "Empty this essentia container into the network", sent by the Essentia Terminal's right-click.
 * It is a payload rather than a menu click because AE2's terminal packets move one item while
 * this empties a container: a jar comes back empty and a phial as glass, which no "transfer
 * slot N" expresses. It names a slot only, and the receiving menu re-reads it, so the payload
 * carries no stack.
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
