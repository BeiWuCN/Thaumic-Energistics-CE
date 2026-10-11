package thaumicenergistics_ce.registry.capability;

import java.util.function.IntPredicate;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

public final class SlotRangeItemHandler extends SnapshotJournal<ItemStack[]> implements ResourceHandler<ItemResource> {

    private final Container container;
    private final int firstSlot;
    private final int slotCount;
    private final IntPredicate band;

    private final boolean readable;

    public SlotRangeItemHandler(Container container, int firstSlot, int slotCount) {
        this(container, firstSlot, slotCount, slot -> true, true);
    }

    public SlotRangeItemHandler(Container container, int firstSlot, int slotCount, IntPredicate band) {
        this(container, firstSlot, slotCount, band, true);
    }

    private SlotRangeItemHandler(
            Container container, int firstSlot, int slotCount, IntPredicate band, boolean readable) {
        this.container = container;
        this.band = band;
        this.readable = readable;
        this.firstSlot = firstSlot;
        this.slotCount = Math.max(0, Math.min(slotCount, container.getContainerSize() - firstSlot));
    }

    private boolean reaches(int index) {
        return readable && index >= 0 && index < slotCount && band.test(firstSlot + index);
    }

    private boolean covers(int index) {
        return index >= 0 && index < slotCount && band.test(firstSlot + index);
    }

    public static SlotRangeItemHandler inputOnly(Container container, int firstSlot, int slotCount) {
        return new SlotRangeItemHandler(container, firstSlot, slotCount, slot -> true, false);
    }


    @Override
    public int size() {
        return slotCount;
    }

    @Override
    public ItemResource getResource(int index) {
        return reaches(index) ? ItemResource.of(container.getItem(firstSlot + index)) : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return reaches(index) ? container.getItem(firstSlot + index).getCount() : 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        int containerSlot = covers(index) ? firstSlot + index : -1;
        if (containerSlot < 0 || resource.isEmpty()) {
            return containerSlot < 0 ? 0 : container.getMaxStackSize();
        }
        if (!insertable(containerSlot, resource.toStack(1))) {
            return 0;
        }
        return Math.min(container.getMaxStackSize(), resource.getMaxStackSize());
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return reaches(index) && !resource.isEmpty() && insertable(firstSlot + index, resource.toStack(1));
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount <= 0 || !isValid(index, resource)) {
            return 0;
        }
        int containerSlot = firstSlot + index;
        ItemStack held = container.getItem(containerSlot);
        if (!held.isEmpty() && !resource.matches(held)) {
            return 0;
        }
        int limit = Math.min(container.getMaxStackSize(), resource.getMaxStackSize());
        int room = held.isEmpty() ? limit : limit - held.getCount();
        int moved = Math.min(room, amount);
        if (moved <= 0) {
            return 0;
        }

        updateSnapshots(transaction);
        if (held.isEmpty()) {
            container.setItem(containerSlot, resource.toStack(moved));
        } else {
            held.grow(moved);
            container.setItem(containerSlot, held);
        }
        return moved;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        if (amount <= 0 || !reaches(index)) {
            return 0;
        }
        int containerSlot = firstSlot + index;
        ItemStack held = container.getItem(containerSlot);
        if (held.isEmpty() || !resource.matches(held)) {
            return 0;
        }
        int moved = Math.min(amount, held.getCount());
        if (moved <= 0) {
            return 0;
        }

        updateSnapshots(transaction);
        if (moved >= held.getCount()) {
            container.setItem(containerSlot, ItemStack.EMPTY);
        } else {
            held.shrink(moved);
            container.setItem(containerSlot, held);
        }
        return moved;
    }


    @Override
    protected ItemStack[] createSnapshot() {
        ItemStack[] snapshot = new ItemStack[slotCount];
        for (int i = 0; i < slotCount; i++) {
            snapshot[i] = container.getItem(firstSlot + i).copy();
        }
        return snapshot;
    }

    @Override
    protected void revertToSnapshot(ItemStack[] snapshot) {
        for (int i = 0; i < snapshot.length; i++) {
            container.setItem(firstSlot + i, snapshot[i].copy());
        }
    }

    @Override
    protected void onRootCommit(ItemStack[] originalState) {
        container.setChanged();
    }


    private boolean insertable(int containerSlot, ItemStack stack) {
        return !stack.isEmpty() && container.canPlaceItem(containerSlot, stack);
    }
}
