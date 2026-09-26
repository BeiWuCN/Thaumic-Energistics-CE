package thaumicenergistics.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.menu.MenuDistillationEncoder;

/**
 * One well in the Distillation Encoder's aspect row.
 *
 * <p>Not an item slot in any sense the player cares about. What is drawn there represents an aspect, and
 * the click means "use this one" - so nothing may be placed and nothing may be taken. Letting vanilla
 * handle a click would let a player pull a phantom item out of a display, which is why
 * {@link MenuDistillationEncoder#clicked} intercepts these slots before vanilla sees them and this class
 * refuses both directions as a second line.
 *
 * <p>The slot index is the aspect's position in the row, not an inventory index: slot {@code i} is always
 * the i-th aspect the source item offers, and {@code -1} marks the separate display of the picked one.
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
