package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The server's half of the Knowledge Inscriber's crafting grid. It has the same shape as
 * {@link GhostGridSlot} and no payload, because the server already holds the grid the machine
 * reads and both sides must lay out the same slot count. Picking up is refused, since here a
 * click would let a player pull out ingredients they never put in.
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
