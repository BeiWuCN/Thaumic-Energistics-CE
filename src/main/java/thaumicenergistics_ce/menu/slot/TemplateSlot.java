package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Distillation Encoder's source well: a <b>template</b> slot naming the item to distil.
 * <ul>
 *   <li>The item is never handed over - the job pays it - so placing, taking and reading use one stack.
 *   <li>JEI drag safety: an ordinary slot would hand a dragged item over for free, duplicating it.
 *   <li>Unlike a read-only display it still syncs: {@code set} is untouched.
 * </ul>
 */
public class TemplateSlot extends Slot {

    public TemplateSlot(Container container, int index, int x, int y) {
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
    public boolean isHighlightable() {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
