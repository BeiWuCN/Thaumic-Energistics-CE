package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;

/**
 * One cell of the Knowledge Inscriber's crafting grid, written by the client. The grid is <em>ghost</em>,
 * so the slot cannot be real, and only the slot and stack travel - the server decides what the grid
 * means.
 *
 * @param containerSlot the container index, <em>not</em> the grid index; the receiver owns the offset
 */
public record InscriberGridPayload(int containerId, int containerSlot, ItemStack stack) implements CustomPacketPayload {

    public static final Type<InscriberGridPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ThEIds.MODID, "inscriber_grid"));

    public static final StreamCodec<RegistryFriendlyByteBuf, InscriberGridPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    InscriberGridPayload::containerId,
                    ByteBufCodecs.VAR_INT,
                    InscriberGridPayload::containerSlot,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    InscriberGridPayload::stack,
                    InscriberGridPayload::new);

    @Override
    public Type<InscriberGridPayload> type() {
        return TYPE;
    }

    public void handle(Player player) {
        if (!(player.containerMenu instanceof KnowledgeInscriberReceiver receiver)
                || receiver.containerId() != containerId) {
            return;
        }
        // The payload carries the container index, not the grid position; translating on receipt is what
        // keeps the two from drifting - reading one as the other drops every cell as out of range.
        int cell = containerSlot - receiver.gridSlotStart();
        if (cell < 0 || cell >= receiver.gridSlotCount()) {
            return;
        }
        receiver.setGridCell(player, cell, stack);
    }
}
