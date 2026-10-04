package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.menu.MenuDistillationEncoder;

/**
 * One well in the Distillation Encoder's aspect row.
 * <ul>
 *   <li>Nothing may be placed or taken: a click means "use this one", so the menu intercepts it first.
 *   <li>The slot index is the aspect's position in the row: slot {@code i} is the i-th aspect the
 *       source item offers, and {@code -1} marks the picked-aspect display.
 * </ul>
 */
public class AspectSelectSlot extends Slot {

    /** Which aspect this slot stands for, or {@code -1} for the picked-aspect display. */
    private final int aspectIndex;

    private final MenuDistillationEncoder menu;

    public AspectSelectSlot(
            Container container, int containerSlot, int x, int y, int aspectIndex, MenuDistillationEncoder menu) {
        super(container, containerSlot, x, y);
        this.aspectIndex = aspectIndex;
        this.menu = menu;
    }

    /** The aspect this slot offers, or {@code -1} for the picked display. */
    public int aspectIndex() {
        return aspectIndex;
    }

    /** Whether this well currently stands for an aspect the source item actually has. */
    public boolean isFilled() {
        return aspectIndex >= 0 && aspectIndex < menu.aspectCount();
    }

    /** Whether this is the well the player has picked. */
    public boolean isSelected() {
        return aspectIndex >= 0 && menu.localSelection() == aspectIndex;
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
