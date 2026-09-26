package thaumicenergistics.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Distillation Encoder's source well: a <b>template</b> slot.
 *
 * <p>It names the item the player wants distilled. The item is never handed over - what a distillation
 * costs is one blank pattern and, later, one of the named item paid by the crafting job that runs the
 * written pattern. So a stack here is a note of intent, not a deposit: placing is refused, taking is
 * refused, and the machine reads the same stack either way.
 *
 * <p><b>This is what makes JEI's drag safe.</b> With an ordinary slot, a target that accepted a dragged
 * item would be handing it over for free - drag anything JEI can list into the well and take it back out
 * of the machine, which is a duplication bug wearing a convenience's clothes. The reference build has the
 * same slot with {@code mayPickup} false for the same reason.
 *
 * <p>Unlike a read-only display slot this one still <em>syncs</em>: {@code set} is untouched, so the
 * server writing the template reaches the client through vanilla's slot packet. Only the player is kept
 * out of both directions.
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
