package thaumicenergistics_ce.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.util.ThELog;

final class InscriberGridState {

    private final MenuKnowledgeInscriber menu;

    private final List<ItemStack> gridScratch = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);

    private int sampledGrid = -1;

    private long gridSignatureTick = Long.MIN_VALUE;

    InscriberGridState(MenuKnowledgeInscriber menu) {
        this.menu = menu;
    }

    void setCell(int cell, ItemStack stack) {
        if (menu.inscriber != null) {
            menu.inscriber.setGridCell(cell, stack);
            return;
        }
        menu.slots.get(MenuKnowledgeInscriber.IDX_CRAFT_START + cell).set(stack);
    }

    List<ItemStack> cells() {
        List<ItemStack> cells = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);
        for (int i = 0; i < MenuKnowledgeInscriber.CRAFT_SLOTS; i++) {
            cells.add(menu.slotStack(MenuKnowledgeInscriber.IDX_CRAFT_START + i));
        }
        return cells;
    }

    int signature() {
        long now = menu.playerInventory.player.level().getGameTime();
        if (now != gridSignatureTick) {
            gridSignatureTick = now;
            gridScratch.clear();
            for (int i = 0; i < MenuKnowledgeInscriber.CRAFT_SLOTS; i++) {
                gridScratch.add(menu.slotStack(MenuKnowledgeInscriber.IDX_CRAFT_START + i));
            }
            sampledGrid = StackSignatures.of(gridScratch);
        }
        return sampledGrid;
    }

    void fillFromRecipe(List<ItemStack> cells) {
        List<ItemStack> full = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);
        for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
            ItemStack stack = cell < cells.size() ? cells.get(cell) : ItemStack.EMPTY;
            full.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
            menu.machine.setItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell, full.get(cell));
        }

        if (menu.inscriber != null) {
            menu.inscriber.setGrid(full);
        } else {
            MenuNetwork.sendInscriberGridFill(menu.containerId, full);
        }
    }

    void loadPattern(int index) {
        List<ItemStack> cells = storedGrid(index);
        if (cells == null) {
            ThELog.LOG.info("[inscriber] pattern well {} holds nothing to load", index);
            return;
        }
        ThELog.LOG.info(
                "[inscriber] loading pattern well {} -> {} ({} cells)",
                index,
                cells.isEmpty() ? "empty grid" : cells.getFirst(),
                cells.size());
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
