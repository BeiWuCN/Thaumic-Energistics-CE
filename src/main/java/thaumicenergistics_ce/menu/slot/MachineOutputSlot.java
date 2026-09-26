package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A well the machine writes into and the player empties: the Distillation Encoder's written pattern.
 *
 * <p>Refuses placement and allows pickup, which is what an output well is. The player cannot put anything
 * in - only {@code encode()} decides what appears there - but they must be able to take the pattern out
 * again, because a written pattern that cannot be retrieved is the same as no pattern at all.
 *
 * <p><b>Why this is not a {@code ReadOnlySlot}.</b> That class overrides {@code set} to a no-op, which
 * reads as harmless for a display and is not: {@code AbstractContainerMenu.setItem} - the method the client
 * runs when the server's slot update arrives - is nothing but {@code getSlot(slotId).set(stack)}, so a slot
 * that swallows {@code set} silently drops every server-to-client write. The pattern was written on the
 * server and never once reached the screen. Nothing here overrides {@code set}, and that is the point.
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
