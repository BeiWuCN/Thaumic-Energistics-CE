package thaumicenergistics_ce.blockentity.inscriber;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.init.ModItems;

/**
 * The inscriber's slots: the core, the mirrors, and the grid the player assembles in the menu.
 * <ul>
 *   <li>A grid write is one change, not nine: the cells go in with the notifications held back.
 *   <li>Saving keeps slot indices, so a grid with gaps comes back with its gaps.
 * </ul>
 */
final class InscriberInventory {

    private final BlockEntityKnowledgeInscriber inscriber;

    private final SimpleContainer inventory =
            new SimpleContainer(BlockEntityKnowledgeInscriber.SLOT_COUNT) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    if (absorbing) {
                        return;
                    }
                    inscriber.setChanged();
                    inscriber.contentsChanged();
                }

                @Override
                public boolean canPlaceItem(int slot, ItemStack stack) {
                    // The core is the only slot that holds an item; the wells beside it only mirror what
                    // the core already stores, so nothing may be put there at all.
                    return slot == BlockEntityKnowledgeInscriber.CORE_SLOT
                            && stack.is(ModItems.KNOWLEDGE_CORE.get());
                }
            };

    /** True while the grid is written cell by cell: a notification per cell would resolve a half-replaced
     * grid, and the player would watch the old recipe's items being shoved out one at a time. */
    private boolean absorbing;

    InscriberInventory(BlockEntityKnowledgeInscriber inscriber) {
        this.inscriber = inscriber;
    }

    SimpleContainer inventory() {
        return inventory;
    }

    List<ItemStack> cells() {
        List<ItemStack> cells = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (int i = 0; i < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; i++) {
            cells.add(inventory.getItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + i));
        }
        return cells;
    }

    void setCell(int cell, ItemStack stack) {
        Level level = inscriber.getLevel();
        if (level == null || level.isClientSide
                || cell < 0 || cell >= BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT) {
            return;
        }
        int slot = BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell;
        ItemStack current = inventory.getItem(slot);
        ItemStack wanted = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (ItemStack.matches(current, wanted)) {
            return;
        }
        inventory.setItem(slot, wanted);
    }

    void setAll(List<ItemStack> cells) {
        Level level = inscriber.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasAbsorbing = absorbing;
        absorbing = true;
        try {
            for (int i = 0; i < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; i++) {
                ItemStack wanted = i < cells.size() ? cells.get(i) : ItemStack.EMPTY;
                inventory.setItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + i,
                        wanted.isEmpty() ? ItemStack.EMPTY : wanted.copyWithCount(1));
            }
        } finally {
            absorbing = wasAbsorbing;
        }
        inscriber.contentsChanged();
    }

    void clear() {
        boolean wasAbsorbing = absorbing;
        absorbing = true;
        try {
            for (int i = 0; i < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; i++) {
                int slot = BlockEntityKnowledgeInscriber.GRID_SLOT_START + i;
                if (!inventory.getItem(slot).isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        } finally {
            absorbing = wasAbsorbing;
        }
    }

    boolean hasCore() {
        return inventory.getItem(BlockEntityKnowledgeInscriber.CORE_SLOT).is(ModItems.KNOWLEDGE_CORE.get());
    }

    ItemStack coreStack() {
        return inventory.getItem(BlockEntityKnowledgeInscriber.CORE_SLOT);
    }

    void saveItems(CompoundTag tag, HolderLookup.Provider registries) {
        // Not SimpleContainer.createTag: that writes only non-empty slots and records no index, so a grid
        // came back with its gaps gone and every item shifted forwards.
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
    }

    void loadItems(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // A world saved before this change kept a bare list under "Inventory", already gap-less: read it
            // positionally and the next save writes the new form.
            loadLegacy(tag.getList("Inventory", Tag.TAG_COMPOUND), registries);
        }
    }

    /** Reads the old bare-list form by position; that form lost which slots its entries came from, and the
     * list may name more slots than this build has. */
    private void loadLegacy(ListTag list, HolderLookup.Provider registries) {
        int kept = Math.min(list.size(), BlockEntityKnowledgeInscriber.SLOT_COUNT);
        for (int i = 0; i < kept; i++) {
            inventory.setItem(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        }
    }

    /** Drops the core and the mirrors when the block is broken. The grid is a scratch pad, not storage. */
    void dropItems() {
        Level level = inscriber.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = inscriber.getBlockPos();
        for (int i = 0; i < BlockEntityKnowledgeInscriber.SLOT_COUNT; i++) {
            // The grid holds items the player still has; dropping them would duplicate what JEI dragged in.
            if (i >= BlockEntityKnowledgeInscriber.GRID_SLOT_START) {
                continue;
            }
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
