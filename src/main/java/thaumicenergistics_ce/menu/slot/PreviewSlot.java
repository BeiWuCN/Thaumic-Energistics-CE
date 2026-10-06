package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A slot that shows an item and does nothing else: no pickup, no placing, no hover highlight.
 * The Arcane Assembler's preview uses it because the block carries no item data in its update
 * tag, so a slot sync is the only way the server puts an item in front of the client.
 * {@code isHighlightable} is false too, since a lit-up preview well reads as somewhere to
 * put things.
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
