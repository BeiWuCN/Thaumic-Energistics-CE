package thaumicenergistics.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.ThEIds;
import thaumicenergistics.menu.MenuEssentiaTerminal;

/**
 * "Empty this essentia container into the network", sent by the Essentia Terminal's right-click.
 *
 * <p>A payload rather than a menu click because AE2's own terminal packets move <em>one item</em> between
 * the player and the network; this moves the contents of a container that stays with the player, which is
 * a different operation with a different result - a jar comes back empty, a phial comes back as glass.
 * None of that is expressible as "transfer slot N to the network".
 *
 * <p>Both the container on the cursor and the one in the main hand are deposited from, so {@code where}
 * says which of the two it was - see {@link ContainerSlot}. The stack travels as well, but only so the
 * server can see what the client thought it was moving; the server re-reads the container from the place
 * it names and refuses when the two disagree, because the inventory can move on between click and packet.
 *
 * @param where the cursor, the main hand, or a menu slot id
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
