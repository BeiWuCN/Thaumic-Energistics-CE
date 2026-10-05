package thaumicenergistics_ce.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.util.ThELog;

/**
 * The menu's 3x3 recipe grid: the write a click makes, the read-back of a stored pattern, and the
 * signature that says whether the grid still resolves the same way.
 * The grid exists on both sides, and a client write is a payload rather than a slot sync - see
 * {@code GhostGridSlot} - so a write here lands on the machine's container or is sent as one.
 */
final class InscriberGridState {

    private final MenuKnowledgeInscriber menu;

    private final List<ItemStack> gridScratch = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);

    private int sampledGrid = -1;

    private long gridSignatureTick = Long.MIN_VALUE;

    InscriberGridState(MenuKnowledgeInscriber menu) {
        this.menu = menu;
    }

    /**
     * One cell, from the menu's click routing: the machine's own container on the server, the slot on
     * the client, whose payload the server turns into the same write on the next tick.
     */
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

    /**
     * Resampled at most once a tick: the components of nine stacks are too much to hash per frame, so
     * a frame that did not advance the tick reuses the last answer.
     */
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

    /**
     * Fills the grid from a recipe's layout, as a JEI transfer and a pattern click do, in one write: a
     * payload per cell made the server re-resolve against a grid that was half the old recipe.
     */
    void fillFromRecipe(List<ItemStack> cells) {
        List<ItemStack> full = new ArrayList<>(MenuKnowledgeInscriber.CRAFT_SLOTS);
        for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
            ItemStack stack = cell < cells.size() ? cells.get(cell) : ItemStack.EMPTY;
            full.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        // 1. This side's grid, in one pass. Writing the container sends no payload.
        for (int cell = 0; cell < MenuKnowledgeInscriber.CRAFT_SLOTS; cell++) {
            menu.machine.setItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell, full.get(cell));
        }

        if (menu.inscriber != null) {
            menu.inscriber.setGrid(full);
        } else {
            MenuNetwork.sendInscriberGridFill(menu.containerId, full);
        }
    }

    /**
     * Reads a stored pattern back onto the grid: the button acts there, so this is also the delete path.
     * The tail is cleared because a shapeless recipe's stored grid is a compact ingredient list.
     */
    void loadPattern(int index) {
        List<ItemStack> cells = storedGrid(index);
        if (cells == null) {
            // Names the well asked for, so an empty well is distinguishable from the wrong one.
            ThELog.LOG.info("[inscriber] pattern well {} holds nothing to load", index);
            return;
        }
        ThELog.LOG.info(
                "[inscriber] loading pattern well {} -> {} ({} cells)",
                index,
                cells.isEmpty() ? "empty grid" : cells.getFirst(),
                cells.size());
        // One replacement, so the grid never holds a mixture of the old recipe and the new.
        menu.fillGridFromRecipe(cells);
    }

    /**
     * The grid of the stored pattern in a well, or {@code null} when it is empty. Read from the core by
     * position: the well's own slots are never filled, so they were stale.
     */
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
