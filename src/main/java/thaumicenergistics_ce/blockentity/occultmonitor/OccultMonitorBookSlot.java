package thaumicenergistics_ce.blockentity.occultmonitor;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.block.BlockOccultMonitor;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import thaumicenergistics_ce.util.ThEItemTags;

/**
 * 监控器唯一的槽、镜像它的两个方块状态，以及右键对典籍做什么。只放得进魔导手册，
 * 只有潜行的玩家能把它取出来；两个方块状态都在这里写，方块类因此只剩一层薄覆写。
 */
final class OccultMonitorBookSlot {

    private final BlockEntityOccultMonitor monitor;

    private final SimpleContainer container = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            monitor.setChanged();
            OccultMonitorBookSlot.this.updateBlockState();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return TcRegistry.isThaumonomicon(stack);
        }
    };

    OccultMonitorBookSlot(BlockEntityOccultMonitor monitor) {
        this.monitor = monitor;
    }

    SimpleContainer container() {
        return container;
    }

    ItemStack book() {
        return container.getItem(BlockEntityOccultMonitor.BOOK_SLOT);
    }

    boolean has() {
        return TcRegistry.isThaumonomicon(book());
    }

    /** 放入典籍，只在玩家潜行时取出。普通右键会把机器解除武装。见 {@code BlockOccultMonitor}。 */
    @Nullable ItemStack interact(ItemStack held, boolean sneaking) {
        if (has()) {
            if (!sneaking || !held.isEmpty()) {
                return null;
            }
            ItemStack removed = book().copy();
            container.setItem(BlockEntityOccultMonitor.BOOK_SLOT, ItemStack.EMPTY);
            return removed;
        }
        if (held.isEmpty() || !TcRegistry.isThaumonomicon(held)) {
            return null;
        }
        ItemStack placed = held.copyWithCount(1);
        held.shrink(1);
        container.setItem(BlockEntityOccultMonitor.BOOK_SLOT, placed);
        return null;
    }

    void updateBlockState() {
        Level level = monitor.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = monitor.getBlockState();
        if (!state.hasProperty(BlockOccultMonitor.BOOK)) {
            return;
        }
        boolean present = has();
        if (state.getValue(BlockOccultMonitor.BOOK) != present) {
            level.setBlock(monitor.getBlockPos(), state.setValue(BlockOccultMonitor.BOOK, present), 3);
        }
    }

    void updateNetworkState() {
        Level level = monitor.getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = monitor.getBlockState();
        if (!state.hasProperty(BlockOccultMonitor.NETWORK)) {
            return;
        }
        boolean online = monitor.getMainNode().isActive();
        if (state.getValue(BlockOccultMonitor.NETWORK) != online) {
            level.setBlock(monitor.getBlockPos(), state.setValue(BlockOccultMonitor.NETWORK, online), 3);
        }
    }

    void save(ValueOutput output) {
        // 空槽也必须写这个字段：ItemStack.CODEC 拒收数量为零的物品堆，会让整次存档失败，
        // 而 OPTIONAL_CODEC 写出的空 compound 正好是 load() 读得回的那个。
        output.store("Book", ItemStack.OPTIONAL_CODEC, container.getItem(BlockEntityOccultMonitor.BOOK_SLOT));
    }

    void load(ValueInput input) {
        container.setItem(BlockEntityOccultMonitor.BOOK_SLOT,
                input.read("Book", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }

    void drop() {
        Level level = monitor.getLevel();
        if (level == null) {
            return;
        }
        ItemStack book = container.getItem(BlockEntityOccultMonitor.BOOK_SLOT);
        if (!book.isEmpty()) {
            Containers.dropItemStack(level, monitor.getBlockPos().getX() + 0.5,
                    monitor.getBlockPos().getY() + 0.5, monitor.getBlockPos().getZ() + 0.5, book);
            container.setItem(BlockEntityOccultMonitor.BOOK_SLOT, ItemStack.EMPTY);
        }
    }
}
