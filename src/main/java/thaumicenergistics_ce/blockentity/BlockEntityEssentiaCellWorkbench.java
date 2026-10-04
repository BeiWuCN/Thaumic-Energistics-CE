package thaumicenergistics_ce.blockentity;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.item.ItemEssentiaCell;

/**
 * Where a storage cell is told which aspects it may hold.
 * <ul>
 *   <li>The partition lives on the cell item, as AE2's own cells do, so it survives a drive or a chest.
 *   <li>The block holds the cell plus a working copy, because a write-back rebuilds a data component.
 *   <li>{@code syncing} guards load and write-back: a write changes components a naive reload misreads.
 * </ul> */
public class BlockEntityEssentiaCellWorkbench extends ThEBaseBlockEntity implements IUpgradeableObject {

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

    private final ConfigInventory partition = ConfigInventory.configTypes(PARTITION_SLOTS)
            .supportedTypes(Set.of(AEssentiaKeyType.INSTANCE))
            .changeListener(this::storePartitionInCell)
            .build();

    /** The cell's own upgrade slots, re-read per call so a cell swapped inside a menu cannot go stale. */
    private final IUpgradeInventory upgrades = new IUpgradeInventory() {

        @Override
        public int size() {
            // Three even with no cell, because the client builds its own slots from this number.
            return ItemEssentiaCell.UPGRADE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IUpgradeInventory cell = upgradesOfCell();
            return slot < cell.size() ? cell.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            IUpgradeInventory cell = upgradesOfCell();
            if (slot >= cell.size()) {
                return;
            }
            // Onto the cell item the block holds, which is then saved with it.
            cell.setItemDirect(slot, stack);
            BlockEntityEssentiaCellWorkbench.this.setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return upgradesOfCell().isItemValid(slot, stack);
        }

        @Override
        public ItemLike getUpgradableItem() {
            return hasCell() ? getCell().getItem() : Items.AIR;
        }

        @Override
        public int getInstalledUpgrades(ItemLike item) {
            return upgradesOfCell().getInstalledUpgrades(item);
        }

        @Override
        public int getMaxInstalled(ItemLike item) {
            return upgradesOfCell().getMaxInstalled(item);
        }

        @Override
        public void readFromNBT(CompoundTag tag, String key, HolderLookup.Provider registries) {
            // Nothing to read: the cards sit in the cell item's own components, saved along with it.
        }

        @Override
        public void writeToNBT(CompoundTag tag, String key, HolderLookup.Provider registries) {
            // Nothing to write, for the same reason.
        }
    };

    private boolean syncing;

    public BlockEntityEssentiaCellWorkbench(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_CELL_WORKBENCH.get(), pos, state);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public ConfigInventory getPartition() {
        return partition;
    }

    public ItemStack getCell() {
        return inventory.getItem(CELL_SLOT);
    }

    public boolean hasCell() {
        return getCell().getItem() instanceof ItemEssentiaCell;
    }


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

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private IUpgradeInventory upgradesOfCell() {
        if (!hasCell() || !(getCell().getItem() instanceof ItemEssentiaCell cell)) {
            return UpgradeInventories.empty();
        }
        return cell.getUpgrades(getCell());
    }

    public boolean accepts(AEKey key) {
        return key != null && key.getType() == AEssentiaKeyType.INSTANCE;
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId, Inventory playerInventory,
            Player player) {
        return new thaumicenergistics_ce.menu.MenuEssentiaCellWorkbench(containerId, playerInventory, this);
    }


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
