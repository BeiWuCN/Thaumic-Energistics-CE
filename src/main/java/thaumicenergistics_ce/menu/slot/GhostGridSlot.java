package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics_ce.network.InscriberGridPayload;

/**
 * One cell of the Knowledge Inscriber's crafting grid, on the side the player is looking at.
 *
 * <p>A ghost slot: it records what the player wants to encode without taking the item. Putting a stack in
 * a real slot would move it out of the player's inventory, and writing a pattern would then cost the
 * player the ingredients - the ingredients are paid for later, by the crafting job, not here.
 *
 * <p>Because the item never leaves the player, there is nothing for vanilla's slot sync to carry: the
 * client's grid is a scratch pad and the server's grid is the machine's own container. So the write does
 * not go through the slot at all - it is sent as a payload, and the server applies it to its grid. That is
 * also why this class exists on its own rather than as a flag: the server needs a slot that looks the same
 * but never sends anything, and mixing the two in one class invites a client write being sent from the
 * server or vice versa.
 *
 * <p>Picking up is allowed so a cell can be cleared by clicking it, matching how the reference build's
 * ghost grid behaves: the player gets back what they never handed over.
 */
public class GhostGridSlot extends Slot {

    /** The menu id, needed to name the right menu when the payload arrives. */
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
    public void onTake(net.minecraft.world.entity.player.Player player, ItemStack stack) {
        super.onTake(player, stack);
        // Empty, not the stack that was taken. The cell is being cleared, and what the machine needs to
        // hear is the cell's new contents - sending the taken stack would tell it the cell still holds the
        // recipe the player has just removed.
        send(ItemStack.EMPTY);
    }

    private void send(ItemStack stack) {
        PacketDistributor.sendToServer(
                new InscriberGridPayload(containerId, getContainerSlot(), stack.copyWithCount(Math.min(1, stack.getCount()))));
    }
}
