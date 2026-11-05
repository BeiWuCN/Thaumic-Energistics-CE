package com.beiwu.thaumicenergistics_ce.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import com.beiwu.thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import com.beiwu.thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

final class InscriberGridState {

    private final MenuKnowledgeInscriber menu;

    private final List<ItemStack> gridScratch = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);

    InscriberGridState(MenuKnowledgeInscriber menu) {
        this.menu = menu;
    }

    void setCell(int cell, ItemStack stack) {
        if (menu.inscriber != null) {
            menu.inscriber.setGridCell(cell, stack);
            return;
        }
        menu.slots.get(MenuKnowledgeInscriber.IDX_CRAFT_START + cell).set(stack);
        MenuNetwork.sendInscriberGrid(menu.containerId, BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell, stack);
    }

    List<ItemStack> cells() {
        List<ItemStack> cells = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);
        for (int i = 0; i < MenuKnowledgeInscriber.CRAFT_SLOTS; i++) {
            cells.add(menu.slotStack(MenuKnowledgeInscriber.IDX_CRAFT_START + i));
        }
        return cells;
    }

    int signature() {
        gridScratch.clear();
        for (int i = 0; i < MenuKnowledgeInscriber.CRAFT_SLOTS; i++) {
            gridScratch.add(menu.slotStack(MenuKnowledgeInscriber.IDX_CRAFT_START + i));
        }
        return StackSignatures.of(gridScratch);
    }

    void clear() {
        if (menu.inscriber != null) {
            menu.inscriber.clearGrid();
            return;
        }
        menu.fillGridFromRecipe(List.of());
    }

    void fillFromRecipe(List<ItemStack> cells) {
        List<ItemStack> full = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);
        for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
            ItemStack stack = cell < cells.size() ? cells.get(cell) : ItemStack.EMPTY;
            full.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        if (menu.inscriber != null) {
            menu.inscriber.setGrid(full);
        } else {
            for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
                menu.machine.setItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell, full.get(cell));
            }
            MenuNetwork.sendInscriberGridFill(menu.containerId, full);
        }
    }

    void loadPattern(int index) {
        List<ItemStack> cells = storedGrid(index);
        if (cells == null) {
            return;
        }
        menu.fillGridFromRecipe(cells);
    }

    private @Nullable List<ItemStack> storedGrid(int index) {
        if (index < 0) {
            return null;
        }
        HandlerKnowledgeCore core = menu.handler();
        if (core == null) {
            return null;
        }
        List<ThEArcanePattern> patterns = core.patterns();
        return index < patterns.size() ? patterns.get(index).grid() : null;
    }
}
