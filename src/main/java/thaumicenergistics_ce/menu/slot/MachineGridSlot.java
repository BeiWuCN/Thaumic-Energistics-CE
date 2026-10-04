package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The server's half of the Knowledge Inscriber's crafting grid.
 * <ul>
 * <li>Same shape as {@link GhostGridSlot} and no payload: the server already holds the grid the
 * machine reads, and both sides must lay out the same slot count for vanilla's by-index sync.
 * <li>Picking up is refused: on the client a click means "clear this cell", but here it would let a
 * player pull out ingredients they never put in.
 * </ul>
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
