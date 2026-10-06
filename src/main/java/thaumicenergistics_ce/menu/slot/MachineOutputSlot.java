package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A well the machine writes into and the player empties: the Distillation Encoder's written
 * pattern. It refuses placement and allows pickup, because only {@code encode()} writes the
 * pattern. It is not a {@code ReadOnlySlot}: a no-op {@code set} swallows the client's
 * {@code AbstractContainerMenu.setItem} write, so the pattern never reaches the screen.
 */
public class MachineOutputSlot extends Slot {

    public MachineOutputSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }
}
