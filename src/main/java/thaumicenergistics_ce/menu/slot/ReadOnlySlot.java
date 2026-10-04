package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A display-only slot that renders a real stack but refuses all interaction.
 * <ul>
 *   <li>Shows real machine state the player must not take, insert into, or have sorted by a mod.
 *   <li>An empty zero-slot container hides {@code slot.index}/{@code slot.container} from automation.
 *   <li>Every mutating entry point is a no-op; {@link #getItem()} reads the real source.
 * </ul>
 */
public class ReadOnlySlot extends Slot {

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
    }

    @Override
    public void setChanged() {
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
