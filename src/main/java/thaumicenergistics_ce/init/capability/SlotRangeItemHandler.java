package thaumicenergistics_ce.init.capability;

import java.util.function.IntPredicate;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jspecify.annotations.Nullable;

/**
 * 本 mod 的机器以 {@link IItemHandler} 的形式呈现，供漏斗和管道使用。一个区段可以带
 * 空洞，因此机器自己写入的区段不会被管道够到；这也不是 NeoForge 的 {@code InvWrapper}，
 * 后者会向槽位的上限询问能取出多少，于是把只装着一个物品的
 * 机器报成能搬走一整堆。
 */
public final class SlotRangeItemHandler implements IItemHandler {

    private final Container container;
    private final int firstSlot;
    private final int slotCount;
    private final IntPredicate band;
    private final boolean inputOnly;

    public SlotRangeItemHandler(Container container, int firstSlot, int slotCount) {
        this(container, firstSlot, slotCount, slot -> true);
    }

    /** 只能被填充的区段：与管道能看到的槽位相同，但取不出任何东西。 */
    public static SlotRangeItemHandler inputOnly(Container container, int firstSlot, int slotCount) {
        return new SlotRangeItemHandler(container, firstSlot, slotCount, slot -> true, true);
    }

    /** 带空洞的区段：{@code band} 收到的是容器自身的索引。 */
    public SlotRangeItemHandler(Container container, int firstSlot, int slotCount, IntPredicate band) {
        this(container, firstSlot, slotCount, band, false);
    }

    private SlotRangeItemHandler(
            Container container, int firstSlot, int slotCount, IntPredicate band, boolean inputOnly) {
        this.container = container;
        this.band = band;
        this.inputOnly = inputOnly;
        this.firstSlot = firstSlot;
        this.slotCount = Math.min(slotCount, container.getContainerSize() - firstSlot);
    }

    /** 该区段是否覆盖此处理器索引：在范围内，且不是它的空洞之一。 */
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
        if (this.inputOnly || amount <= 0 || !reaches(slot)) {
            return ItemStack.EMPTY;
        }
        return takeFromSlot(firstSlot + slot, amount, simulate);
    }

    /** 插入到整个区段，这是漏斗或管道不指定槽位时所做的搬运。 */
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

    /** level 判断把它挡在客户端之外，客户端上的机器从菜单解析自身内容。 */
    private boolean insertable(int containerSlot, @Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && container.canPlaceItem(containerSlot, stack);
    }

    private void markChanged() {
        container.setChanged();
    }
}
