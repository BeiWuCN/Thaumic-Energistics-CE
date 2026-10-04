package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * One cell of the Knowledge Inscriber's crafting grid, written by the client. The grid is <em>ghost</em>,
 * so the slot cannot be real, and only the slot and stack travel - the server decides what the grid
 * means.
 *
 * @param containerSlot the container index, <em>not</em> the grid index; the offset is
 *     {@code BlockEntityKnowledgeInscriber}'s to define
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

    /** Applies this cell to the open menu, if it is still the inscriber's. */
    public void handle(Player player) {
        // The payload carries the container index, not the grid position; translating on receipt is what
        // keeps the two from drifting - reading one as the other drops every cell as out of range.
        int cell = containerSlot - BlockEntityKnowledgeInscriber.GRID_SLOT_START;
        if (cell < 0 || cell >= BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT) {
            return;
        }
        if (player.containerMenu instanceof MenuKnowledgeInscriber menu
                && menu.containerId == containerId) {
            menu.setGridCell(player, cell, stack);
        }
    }
}
