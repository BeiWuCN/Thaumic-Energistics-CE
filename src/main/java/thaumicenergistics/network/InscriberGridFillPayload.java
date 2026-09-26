package thaumicenergistics.network;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.ThEIds;
import thaumicenergistics.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics.menu.MenuKnowledgeInscriber;

/**
 * The Knowledge Inscriber's whole 3x3 grid, written by the client in one go.
 *
 * <p>Exists because sending it one cell at a time is visible and expensive. The machine resolves the grid
 * whenever it changes, so nine separate writes mean nine full scans of the recipe manager, and eight of them
 * are asking about a grid that is part old recipe and part new - answers nothing wants. The screen shows
 * those intermediate grids too, which is how it was reported: *"让老配方冲进来看3x3区域如同搅拌机一样"*.
 *
 * <p>One message, one write, one resolution. The client has already put the same nine stacks into its own copy
 * of the container before sending this, so what the player sees and what the server is told are the same
 * thing at the same moment.
 *
 * @param containerId the menu this belongs to
 * @param cells exactly nine stacks, in reading order; shorter lists are padded with empties
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

    /** Applies the whole grid to the open menu, if it is still the inscriber's. */
    public void handle(net.minecraft.world.entity.player.Player player) {
        if (player.containerMenu instanceof MenuKnowledgeInscriber menu && menu.containerId == containerId) {
            menu.applyGridFill(player, cells, BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        }
    }
}
