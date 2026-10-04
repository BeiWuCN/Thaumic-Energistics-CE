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
import thaumicenergistics_ce.menu.slot.ContainerSlot;

/**
 * "Draw this aspect out of the network into my container", sent by the Essentia Terminal's left-click.
 * <ul>
 *   <li>{@code aspectId} travels by id, not as a key: a key the client built wrong could not be
 *       matched against server storage.
 *   <li>{@code where} names the container (see {@link ContainerSlot}); {@code stack} is only a hint.
 * </ul>
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
