package thaumicenergistics.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A slot that shows an item and does nothing else: no pickup, no placing, and no hover highlight.
 *
 * <p>Used for the Arcane Assembler's craft preview - the 3x3 of ingredients and the result well - which the
 * panel art already draws as wells. Real slots are the only way the server has of putting an item in front of
 * a client, because the block deliberately carries no item data in its update tag, so the preview has to ride
 * a slot sync even though nothing about it is interactive.
 *
 * <p><b>The highlight is the part worth stating.</b> Vanilla draws a white overlay over the hovered slot, and
 * a preview well that lights up under the cursor looks like somewhere to put things - which it is not. The
 * whole preview is meant to read as part of the panel, so {@code isHighlightable} is false as well as
 * {@code mayPlace} and {@code mayPickup}. Without that the wells would appear to accept items and then refuse
 * every one of them, which is worse than not looking interactive at all.
 */
public class PreviewSlot extends Slot {

    public PreviewSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
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
}
