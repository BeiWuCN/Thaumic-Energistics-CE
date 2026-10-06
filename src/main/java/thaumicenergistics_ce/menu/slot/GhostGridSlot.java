package thaumicenergistics_ce.menu.slot;

import java.util.function.BiConsumer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One cell of the Knowledge Inscriber's crafting grid, on the side the player is looking at.
 * It is a ghost slot: it records what to encode without taking the item, since the job pays
 * later, and the item never leaves the player, so the write goes as a payload rather than
 * through slot sync. Picking up is allowed so that a cell can be cleared by clicking it.
 */
public class GhostGridSlot extends Slot {

    private final BiConsumer<Integer, ItemStack> writer;

    public GhostGridSlot(
            Container container, int index, int x, int y, BiConsumer<Integer, ItemStack> writer) {
        super(container, index, x, y);
        this.writer = writer;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void set(ItemStack stack) {
        super.set(stack);
        request(stack);
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        super.onTake(player, stack);
        // Empty, not the stack that was taken: the machine needs the cell's new contents, and sending the
        // taken stack would say the cell still holds the recipe the player has just removed.
        request(ItemStack.EMPTY);
    }

    private void request(ItemStack stack) {
        writer.accept(getContainerSlot(), stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }
}
