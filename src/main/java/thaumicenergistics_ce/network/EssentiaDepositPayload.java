package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;

/**
 * "Empty this essentia container into the network", sent by the Essentia Terminal's right-click.
 * <ul>
 * <li>A payload, not a menu click: AE2's terminal packets move one item, this empties a container.
 * <li>A jar comes back empty, a phial as glass - no "transfer slot N" expresses that.
 * <li>The server re-reads {@code where} and rejects a stale {@code stack}: the inventory moves on.
 * </ul>
 */
public record EssentiaDepositPayload(int containerId, int where, ItemStack stack) implements CustomPacketPayload {

    public static final Type<EssentiaDepositPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "essentia_terminal_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaDepositPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaDepositPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaDepositPayload::where,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    EssentiaDepositPayload::stack,
                    EssentiaDepositPayload::new);

    @Override
    public Type<EssentiaDepositPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof MenuEssentiaTerminal menu && menu.containerId == containerId) {
            menu.deposit(player, where, stack);
        }
    }
}
