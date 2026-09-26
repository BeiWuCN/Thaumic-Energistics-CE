package thaumicenergistics_ce.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.ThEIds;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * One cell of the Knowledge Inscriber's crafting grid, written by the client.
 *
 * <p>A payload is needed here and not for the machine's other slots because the grid is a <em>ghost</em>
 * grid: it holds what the player intends to encode, not items they are handing over. Nothing is consumed,
 * so the slot cannot be a real one - writing a real slot would take the item out of the player's
 * inventory and make the pattern cost its ingredients.
 *
 * <p>Only the slot and the stack travel. The server does the rest, including deciding whether the grid now
 * means a recipe, so nothing about the machine's state is trusted from the client beyond the items
 * themselves.
 *
 * @param containerSlot the slot's index in the machine's container, <em>not</em> its position in the grid:
 *     the grid's slots start after the core and the pattern mirrors, so the two differ by a constant that
 *     both sides read from {@code BlockEntityKnowledgeInscriber} rather than repeating
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
    public void handle(net.minecraft.world.entity.player.Player player) {
        // The slot travels as the container index it names, and the grid's own slots start past the core
        // and the mirrors. Translating here rather than at the sender keeps the wire format honest about
        // what it carries - and an earlier build sent the container index while the machine read it as a
        // position in the grid, which put every cell past the end of the grid and dropped all nine.
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
