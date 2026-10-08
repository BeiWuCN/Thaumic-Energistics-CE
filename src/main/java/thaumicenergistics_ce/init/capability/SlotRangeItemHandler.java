package thaumicenergistics_ce.init.capability;

import java.util.function.IntPredicate;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 本 mod 的机器以 {@link IItemHandler} 暴露给漏斗和管道。
 * 区段可以带空洞，机器自己写入的区段不会被管道够到。
 * 不用 NeoForge 的 {@code InvWrapper}：它问的是槽位的上限，会把只装一个物品的机器报成能搬走一整堆。
 */
public final class SlotRangeItemHandler extends SnapshotJournal<ItemStack[]> implements ResourceHandler<ItemResource> {

    private final Container container;
    private final int firstSlot;
    private final int slotCount;
    private final IntPredicate band;

    /**
     * 该区段答不答读取。只可填充的区段不答：问它里面有什么，一律答「空的」，
     * 管道因此填得进、取不出；而机器自己的代码握着容器而不是这个视图，仍看得到它写进去的东西。
     */
    private final boolean readable;

    public SlotRangeItemHandler(Container container, int firstSlot, int slotCount) {
        this(container, firstSlot, slotCount, slot -> true, true);
    }

    /** 带空洞的区段；{@code band} 收到的是容器自身的索引。 */
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

    /** 该区段是否覆盖此处理器索引：在范围内，且不是空洞。 */
    private boolean reaches(int index) {
        return readable && index >= 0 && index < slotCount && band.test(firstSlot + index);
    }

    /** 该区段是否覆盖此处理器索引，不论它答不答读取。 */
    private boolean covers(int index) {
        return index >= 0 && index < slotCount && band.test(firstSlot + index);
    }

    /** 只可填充的区段：槽位与管道看到的一样，但取不出东西。 */
    public static SlotRangeItemHandler inputOnly(Container container, int firstSlot, int slotCount) {
        return new SlotRangeItemHandler(container, firstSlot, slotCount, slot -> true, false);
    }

    // ------------------------------------------------------------------
    // ResourceHandler
    // ------------------------------------------------------------------

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

    /**
     * 槽位的上限取容器与物品堆两者的较小值，所以只收一张卡的槽位仍报一，
     * 槽位会拒收的东西报零。
     */
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
        // 资源是类型不是物品堆：空间归槽位，问容器自己的过滤器拿一份这个类型就够。
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

    // ------------------------------------------------------------------
    // 事务
    // ------------------------------------------------------------------

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

    /**
     * 推迟到提交时做，不在每次写入时做：搬运中途通知容器已改变，
     * 事务随后又回滚，就会保存并同步一个机器其实没到过的状态。
     */
    @Override
    protected void onRootCommit(ItemStack[] originalState) {
        container.setChanged();
    }

    // ------------------------------------------------------------------
    // 槽位自己的过滤器
    // ------------------------------------------------------------------

    /** 容器收不收这个：问的是它自己的规则，不是区段的。 */
    private boolean insertable(int containerSlot, ItemStack stack) {
        return !stack.isEmpty() && container.canPlaceItem(containerSlot, stack);
    }
}
