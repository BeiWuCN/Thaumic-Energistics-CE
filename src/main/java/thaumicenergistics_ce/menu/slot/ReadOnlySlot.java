package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A display-only slot that renders a real stack but refuses all interaction.
 *
 * <p>Used for the assembler's pattern mirror and its target-output preview. Both show genuine machine
 * state that the player must not be able to take, insert into, or have rearranged by an inventory
 * sorting mod.
 *
 * <p>The technique is to hand the superclass an empty zero-slot container, so {@code slot.index} and
 * {@code slot.container} never expose a real inventory to third-party automation, and then override
 * every mutating entry point to a no-op while {@link #getItem()} reads the real source.
 */
public class ReadOnlySlot extends Slot {

    /** Placeholder so no external code can reach a real inventory through this slot. */
    private static final Container PLACEHOLDER = new SimpleContainer(0);

    private final Container source;
    private final int sourceIndex;

    public ReadOnlySlot(Container source, int sourceIndex, int x, int y) {
        super(PLACEHOLDER, 0, x, y);
        this.source = source;
        this.sourceIndex = sourceIndex;
    }

    @Override
    public ItemStack getItem() {
        return source.getItem(sourceIndex);
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
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
    public void set(ItemStack stack) {
        // Display only.
    }

    @Override
    public void setChanged() {
        // Display only.
    }

    @Override
    public int getMaxStackSize() {
        return 0;
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }
}
