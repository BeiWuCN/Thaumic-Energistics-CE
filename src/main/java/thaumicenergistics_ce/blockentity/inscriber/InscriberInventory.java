package thaumicenergistics_ce.blockentity.inscriber;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.registry.TECEItems;

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
                    return slot == BlockEntityKnowledgeInscriber.CORE_SLOT
                            && stack.is(TECEItems.KNOWLEDGE_CORE.get());
                }
            };

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
        if (level == null || level.isClientSide()
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
        if (level == null || level.isClientSide()) {
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
        return inventory.getItem(BlockEntityKnowledgeInscriber.CORE_SLOT).is(TECEItems.KNOWLEDGE_CORE.get());
    }

    ItemStack coreStack() {
        return inventory.getItem(BlockEntityKnowledgeInscriber.CORE_SLOT);
    }

    void saveItems(ValueOutput output) {
        ContainerHelper.saveAllItems(output, inventory.getItems());
    }

    void loadItems(ValueInput input) {
        if (input.childrenList(ContainerHelper.TAG_ITEMS).isPresent()) {
            ContainerHelper.loadAllItems(input, inventory.getItems());
        } else {
            loadLegacy(input.read("Inventory", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of()));
        }
    }

    private void loadLegacy(List<ItemStack> list) {
        int kept = Math.min(list.size(), BlockEntityKnowledgeInscriber.SLOT_COUNT);
        for (int i = 0; i < kept; i++) {
            inventory.setItem(i, list.get(i));
        }
    }

    void dropItems() {
        Level level = inscriber.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = inscriber.getBlockPos();
        for (int i = 0; i < BlockEntityKnowledgeInscriber.SLOT_COUNT; i++) {
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
