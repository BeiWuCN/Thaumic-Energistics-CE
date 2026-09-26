package thaumicenergistics.blockentity;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.ConfigInventory;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics.block.ThEBaseBlockEntity;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.integration.ae2.AEssentiaKeyType;
import thaumicenergistics.item.ItemEssentiaCell;

/**
 * The Essentia Cell Workbench: where a storage cell is told which aspects it may hold.
 *
 * <p>A cell's partition is stored on the cell item itself, as AE2's own cells do - that is what lets a
 * partitioned cell keep its setting in a drive, a chest, or a player's hand. The workbench is the place to
 * edit it, not the place to keep it.
 *
 * <p>So this block holds two things: the cell, and a working copy of its partition. The copy exists
 * because the grid of wells on screen has to be edited slot by slot and a player expects the edit to be
 * immediate; writing through to the item on every keystroke would mean rebuilding a data component per
 * slot. Inserting a cell loads its partition into the copy, and editing the copy writes back.
 *
 * <p>The write-back is guarded against re-entering itself. Writing the copy fires its change listener,
 * which writes the cell, which changes the cell's components - and a naive implementation would treat
 * that as a new cell and reload the copy from it, discarding the edit in progress.
 */
public class BlockEntityEssentiaCellWorkbench extends ThEBaseBlockEntity {

    /** The one machine slot: the cell being configured. */
    public static final int CELL_SLOT = 0;

    /** Partition entries, matching AE2's own cell workbench and the 7x9 grid in the screen's art. */
    public static final int PARTITION_SLOTS = 63;

    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (syncing) {
                return;
            }
            BlockEntityEssentiaCellWorkbench.this.setChanged();
            BlockEntityEssentiaCellWorkbench.this.loadPartitionFromCell();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof ItemEssentiaCell;
        }
    };

    /**
     * The partition being edited.
     *
     * <p>Essentia only: the wells must not offer items or fluids this cell cannot hold.
     */
    private final ConfigInventory partition = ConfigInventory.configTypes(PARTITION_SLOTS)
            .supportedTypes(Set.of(AEssentiaKeyType.INSTANCE))
            .changeListener(this::storePartitionInCell)
            .build();

    /** True while this block is copying between the cell and the partition, to stop the loop. */
    private boolean syncing;

    public BlockEntityEssentiaCellWorkbench(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_CELL_WORKBENCH.get(), pos, state);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    /** The partition being edited, for the menu's grid. */
    public ConfigInventory getPartition() {
        return partition;
    }

    /** The cell in the slot, or empty. */
    public ItemStack getCell() {
        return inventory.getItem(CELL_SLOT);
    }

    public boolean hasCell() {
        return getCell().getItem() instanceof ItemEssentiaCell;
    }

    // ------------------------------------------------------------------
    // Keeping the partition and the cell in step
    // ------------------------------------------------------------------

    /**
     * Replaces the partition being edited with whatever the inserted cell holds.
     *
     * <p>An empty slot clears the grid rather than leaving the previous cell's partition on screen, which
     * would let a player partition a cell that is no longer there.
     */
    private void loadPartitionFromCell() {
        syncing = true;
        try {
            for (int slot = 0; slot < PARTITION_SLOTS; slot++) {
                partition.setStack(slot, null);
            }
            if (!hasCell()) {
                return;
            }
            ConfigInventory cellPartition = ItemEssentiaCell.partitionOf(getCell());
            if (cellPartition == null) {
                return;
            }
            for (int slot = 0; slot < Math.min(PARTITION_SLOTS, cellPartition.size()); slot++) {
                AEKey key = cellPartition.getKey(slot);
                if (key != null) {
                    partition.setStack(slot, new GenericStack(key, 1));
                }
            }
        } finally {
            syncing = false;
        }
    }

    /**
     * Writes the edited partition back onto the cell.
     *
     * <p>Through the cell's own config inventory rather than by rebuilding its data component: that is the
     * same object AE2 reads when the cell is used, so there is one representation rather than two.
     */
    private void storePartitionInCell() {
        if (syncing || !hasCell()) {
            return;
        }
        ConfigInventory cellPartition = ItemEssentiaCell.partitionOf(getCell());
        if (cellPartition == null) {
            return;
        }
        syncing = true;
        try {
            for (int slot = 0; slot < cellPartition.size(); slot++) {
                AEKey key = slot < PARTITION_SLOTS ? partition.getKey(slot) : null;
                cellPartition.setStack(slot, key == null ? null : new GenericStack(key, 1));
            }
            setChanged();
        } finally {
            syncing = false;
        }
    }

    /** Asks the cell whether an aspect is one it will accept. Used by the screen's labels. */
    public boolean accepts(AEKey key) {
        return key != null && key.getType() == AEssentiaKeyType.INSTANCE;
    }

    @Override
    public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
            int containerId, net.minecraft.world.entity.player.Inventory playerInventory,
            net.minecraft.world.entity.player.Player player) {
        return new thaumicenergistics.menu.MenuEssentiaCellWorkbench(containerId, playerInventory, this);
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    /**
     * Only the cell is saved.
     *
     * <p>The partition is not stored here, and that is deliberate: it lives on the cell item, which is
     * what has to survive being taken out and put in a drive. Writing a second copy into the block would
     * be a second thing to keep in step, and the one that loses would be whichever was read last.
     */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // ContainerHelper, not SimpleContainer.createTag. createTag writes a bare list of the non-empty slots
        // with no index on any entry, so reading it back by position is right only while no slot before a full
        // one can ever be empty. One slot makes that true here - which is the kind of true that stops being
        // true the day a second slot is added, and by then the worlds are already saved. The index costs
        // nothing and is the form the Distillation Encoder and the Knowledge Inscriber write.
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // A world saved before this change kept the bare list under "Inventory". There is one slot, so the
            // cell is at position 0 or nowhere, and reading it there cannot be wrong.
            var list = tag.getList("Inventory", CompoundTag.TAG_COMPOUND);
            if (!list.isEmpty()) {
                inventory.setItem(CELL_SLOT, ItemStack.parseOptional(registries, list.getCompound(0)));
            }
        }
        // After the inventory, so a cell that was saved comes back with its partition on screen.
        loadPartitionFromCell();
    }

    /** Drops the cell when the block is broken. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        var stack = inventory.getItem(CELL_SLOT);
        if (!stack.isEmpty()) {
            net.minecraft.world.Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    stack);
            inventory.setItem(CELL_SLOT, ItemStack.EMPTY);
        }
    }
}
