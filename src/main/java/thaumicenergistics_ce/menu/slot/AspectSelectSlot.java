package thaumicenergistics_ce.menu.slot;

import java.util.function.IntSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * One well in the Distillation Encoder's aspect row.
 * <ul>
 *   <li>Nothing may be placed or taken: a click means "use this one", so the menu intercepts it first.
 *   <li>The slot index is the aspect's position in the row: slot {@code i} is the i-th aspect the
 *       source item offers, and {@code -1} marks the picked-aspect display.
 * </ul>
 */
public class AspectSelectSlot extends Slot {

    private final int aspectIndex;

    // The row's size and its picked aspect arrive as reads rather than as the menu itself: a slot that
    // names its menu is one half of the menu <-> slot cycle.
    private final IntSupplier aspectCount;

    private final IntSupplier selection;

    public AspectSelectSlot(
            Container container,
            int containerSlot,
            int x,
            int y,
            int aspectIndex,
            IntSupplier aspectCount,
            IntSupplier selection) {
        super(container, containerSlot, x, y);
        this.aspectIndex = aspectIndex;
        this.aspectCount = aspectCount;
        this.selection = selection;
    }

    public int aspectIndex() {
        return aspectIndex;
    }

    public boolean isFilled() {
        return aspectIndex >= 0 && aspectIndex < aspectCount.getAsInt();
    }

    public boolean isSelected() {
        return aspectIndex >= 0 && selection.getAsInt() == aspectIndex;
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
