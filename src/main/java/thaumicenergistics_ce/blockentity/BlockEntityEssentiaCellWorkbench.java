package thaumicenergistics_ce.blockentity;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.util.ConfigInventory;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.item.ItemEssentiaCell;

/**
 * Where a storage cell is told which aspects it may hold.
 * <ul>
 *   <li>The partition lives on the cell item, as AE2's own cells do, so it survives a drive, a chest or a
 *       player's hand. The workbench edits it, it does not keep it.
 *   <li>The block holds the cell plus a working copy of its partition, because the wells are edited a slot
 *       at a time and a write-back rebuilds a data component.
 *   <li>Load and write-back are guarded by {@code syncing}: writing the cell changes its components, which
 *       a naive reload reads as a new cell.
 * </ul> */
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

    /** The partition being edited. Essentia only: the wells must not offer items or fluids. */
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

    // ---- Keeping the partition and the cell in step ----

    /** Replaces the edited partition with whatever the cell holds; an empty slot clears the grid. */
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

    /** Writes the edited partition back onto the cell's own config inventory: the object AE2 reads. */
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
    public AbstractContainerMenu createMenu(
            int containerId, Inventory playerInventory,
            Player player) {
        return new thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench(containerId, playerInventory, this);
    }

    // ---- Persistence ----

    /** Only the cell is saved: the partition lives on the cell item, and a second copy here would drift. */
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // ContainerHelper, not createTag: createTag writes no index, so any gap shifts every item.
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // Old worlds kept a bare list under "Inventory"; one slot means position 0 or nothing.
            var list = tag.getList("Inventory", CompoundTag.TAG_COMPOUND);
            if (!list.isEmpty()) {
                inventory.setItem(CELL_SLOT, ItemStack.parseOptional(registries, list.getCompound(0)));
            }
        }
        // After the inventory, so a saved cell comes back with its partition on screen.
        loadPartitionFromCell();
    }

    /** Drops the cell when the block is broken. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        var stack = inventory.getItem(CELL_SLOT);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    stack);
            inventory.setItem(CELL_SLOT, ItemStack.EMPTY);
        }
    }
}
