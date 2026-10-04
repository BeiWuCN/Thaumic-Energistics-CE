package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.network.InscriberGridPayload;

/**
 * One cell of the Knowledge Inscriber's crafting grid, on the side the player is looking at.
 *
 * <ul>
 * <li>A ghost slot: it records what the player wants to encode without taking the item, because the
 * ingredients are paid for later by the crafting job, not when the pattern is written.</li>
 * <li>The item never leaves the player, so vanilla's slot sync has nothing to carry and the write is
 * sent as a payload instead. The server therefore needs its own slot class that looks the same but
 * never sends anything - mixing both roles in one class invites a write in the wrong direction.</li>
 * <li>Picking up is allowed so a cell can be cleared by clicking it.</li>
 * </ul>
 */
public class GhostGridSlot extends Slot {

    /** The menu id, naming the right menu when the payload arrives. */
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

    /** Records the cell locally and tells the server, which owns the grid the machine reads. */
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
