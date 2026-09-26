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
 * "Draw this aspect out of the network and into my container", sent by the Essentia Terminal's
 * left-click on a list entry.
 *
 * <p>The aspect travels by id rather than as an {@code AEssentiaKey}, because an id is what the client is
 * sure of - the entry it clicked carries one - and the server rebuilds the key from it. Sending the key
 * would mean the client asserting a type, and a key it built wrong would be a key the server cannot match
 * against its own storage.
 *
 * <p>{@code where} names the container the essentia is wanted in - the cursor stack or the main hand, see
 * {@link ContainerSlot} - and {@code stack} travels as a hint the server checks itself against rather than
 * trusts: it fills the stack it finds at that place, not the one it was sent.
 */
public record EssentiaFillPayload(int containerId, ResourceLocation aspectId, int where, ItemStack stack)
        implements CustomPacketPayload {

    public static final Type<EssentiaFillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "essentia_terminal_fill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EssentiaFillPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    EssentiaFillPayload::containerId,
                    ResourceLocation.STREAM_CODEC,
                    EssentiaFillPayload::aspectId,
                    ByteBufCodecs.VAR_INT,
                    EssentiaFillPayload::where,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    EssentiaFillPayload::stack,
                    EssentiaFillPayload::new);

    @Override
    public Type<EssentiaFillPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (player.containerMenu instanceof MenuEssentiaTerminal menu && menu.containerId == containerId) {
            menu.fillFromNetwork(player, where, aspectId);
        }
    }
}
