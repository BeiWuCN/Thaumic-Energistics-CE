package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.network.InscriberGridPayload;

/**
 * One cell of the Knowledge Inscriber's crafting grid, on the side the player is looking at.
 * <ul>
 *   <li>A ghost slot: it records what to encode without taking the item, since the job pays later.
 *   <li>The item never leaves the player, so the write goes as a payload, not through slot sync.
 *   <li>Picking up is allowed so a cell can be cleared by clicking it.
 * </ul>
 */
public class GhostGridSlot extends Slot {

    private final int containerId;

    public GhostGridSlot(Container container, int index, int x, int y, int containerId) {
        super(container, index, x, y);
        this.containerId = containerId;
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
        send(stack);
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        super.onTake(player, stack);
        // Empty, not the stack that was taken: the machine needs the cell's new contents, and sending the
        // taken stack would say the cell still holds the recipe the player has just removed.
        send(ItemStack.EMPTY);
    }

    private void send(ItemStack stack) {
        PacketDistributor.sendToServer(
                new InscriberGridPayload(containerId, getContainerSlot(), stack.copyWithCount(Math.min(1, stack.getCount()))));
    }
}
