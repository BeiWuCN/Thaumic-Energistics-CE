package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A slot that shows an item and does nothing else: no pickup, no placing, no hover highlight.
 * <ul>
 * <li>Used for the Arcane Assembler's preview: the block carries no item data in its update tag,
 * so a slot sync is the only way the server puts an item in front of the client.
 * <li>{@code isHighlightable} is false too: a lit-up preview well reads as somewhere to put things.
 * </ul>
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
