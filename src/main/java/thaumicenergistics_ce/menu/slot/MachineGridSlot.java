package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The server's half of the Knowledge Inscriber's crafting grid.
 *
 * <p>Same shape as {@link GhostGridSlot} and no payload: the server already holds the grid the machine
 * reads, and this slot is only here so both sides lay out the same number of slots at the same
 * coordinates.  Vanilla's slot sync compares the two by index, so they have to line up.
 *
 * <p>Picking up is refused. On the client a click means "clear this cell"; here it would mean taking an
 * item out of the machine, which would let a player pull ingredients they never put in.
 */
public class MachineGridSlot extends Slot {

    public MachineGridSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
