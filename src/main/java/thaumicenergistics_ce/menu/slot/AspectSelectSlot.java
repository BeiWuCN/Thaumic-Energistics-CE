package thaumicenergistics_ce.menu.slot;

import java.util.function.IntSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class AspectSelectSlot extends Slot {

    private final int aspectIndex;

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
