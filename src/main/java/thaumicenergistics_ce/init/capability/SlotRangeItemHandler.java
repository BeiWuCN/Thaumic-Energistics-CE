package thaumicenergistics_ce.init.capability;

import java.util.function.IntPredicate;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

/**
 * The mod's machines seen as an {@link IItemHandler}, for hoppers and pipes.
 * <ul>
 *   <li>A band can have holes, so a band the machine writes for itself stays out of a pipe's reach.
 *   <li>Not NeoForge's {@code InvWrapper}, which asks a slot's limit how much may come out and so
 *       reports a machine holding one item as able to move a stack.
 * </ul>
 */
public final class SlotRangeItemHandler implements IItemHandler {

    private final Container container;
    private final int firstSlot;
    private final int slotCount;
    private final IntPredicate band;

    public SlotRangeItemHandler(Container container, int firstSlot, int slotCount) {
        this(container, firstSlot, slotCount, slot -> true);
    }

    /** A band with holes: {@code band} is asked about the container's own index. */
    public SlotRangeItemHandler(Container container, int firstSlot, int slotCount, IntPredicate band) {
        this.container = container;
        this.band = band;
        this.firstSlot = firstSlot;
        this.slotCount = Math.min(slotCount, container.getContainerSize() - firstSlot);
    }

    /** Whether the band reaches this handler index: in range, and not one of its holes. */
    private boolean reaches(int slot) {
        return slot >= 0 && slot < slotCount && band.test(firstSlot + slot);
    }

    @Override
    public int getSlots() {
        return slotCount;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return reaches(slot) ? container.getItem(firstSlot + slot) : ItemStack.EMPTY;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return reaches(slot) && insertable(firstSlot + slot, stack);
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !isItemValid(slot, stack)) {
            return stack;
        }
        return insertIntoSlot(firstSlot + slot, stack, simulate);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || !reaches(slot)) {
            return ItemStack.EMPTY;
        }
        return takeFromSlot(firstSlot + slot, amount, simulate);
    }

    /** Inserts into the whole band, the move a hopper or a pipe makes when it names no slot. */
    public ItemStack insertEverywhere(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack;
        for (int slot = 0; slot < slotCount && !remaining.isEmpty(); slot++) {
            remaining = insertIntoSlot(firstSlot + slot, remaining, simulate);
        }
        return remaining;
    }

    private ItemStack insertIntoSlot(int containerSlot, ItemStack stack, boolean simulate) {
        if (!band.test(containerSlot) || !insertable(containerSlot, stack)) {
            return stack;
        }
        ItemStack held = container.getItem(containerSlot);
        int limit = Math.min(container.getMaxStackSize(), stack.getMaxStackSize());
        int room = held.isEmpty() ? limit : limit - held.getCount();
        if (room <= 0 || (!held.isEmpty() && !ItemStack.isSameItemSameComponents(held, stack))) {
            return stack;
        }
        int moved = Math.min(room, stack.getCount());
        if (!simulate) {
            if (held.isEmpty()) {
                container.setItem(containerSlot, stack.copyWithCount(moved));
            } else {
                held.grow(moved);
                container.setItem(containerSlot, held);
            }
            markChanged();
        }
        return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
    }

    private ItemStack takeFromSlot(int containerSlot, int amount, boolean simulate) {
        ItemStack held = container.getItem(containerSlot);
        if (held.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = held.copyWithCount(Math.min(amount, held.getCount()));
        if (!simulate) {
            held.shrink(taken.getCount());
            container.setItem(containerSlot, held.isEmpty() ? ItemStack.EMPTY : held);
            markChanged();
        }
        return taken;
    }

    /** The level guard keeps this off the client, where the machines resolve their contents from a menu. */
    private boolean insertable(int containerSlot, @Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && container.canPlaceItem(containerSlot, stack);
    }

    private void markChanged() {
        container.setChanged();
    }
}
