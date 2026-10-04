package thaumicenergistics_ce.net;

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
 * The Knowledge Inscriber's whole 3x3 grid, written by the client in one go.
 * <ul>
 * <li>Per-cell updates are visible and make the machine re-resolve the grid nine times over.
 * <li>The client applies the same nine stacks to its own container copy first, so both sides agree.
 * <li>{@code cells} is exactly nine stacks in reading order; a shorter list is padded with empties.
 * </ul>
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
