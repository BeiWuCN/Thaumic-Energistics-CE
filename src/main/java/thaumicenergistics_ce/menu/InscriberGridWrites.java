package thaumicenergistics_ce.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/**
 * What a click or a payload writes into the 3x3 grid: a cell takes the carried stack, a well loads the
 * pattern it holds, and the two payloads carry one cell or a whole grid over.
 * The payload half runs server only, since a client write lands in a container the server never sees.
 */
final class InscriberGridWrites {

    private InscriberGridWrites() {}

    /**
     * Routes a click that landed on the recipe grid or a pattern well. True when it was handled, so the
     * caller must not hand it on to the vanilla slot logic.
     */
    static boolean route(
            MenuKnowledgeInscriber menu, InscriberGridState grid, int slotId, ClickType clickType) {
        if (slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }
        int cell = slotId - MenuKnowledgeInscriber.IDX_CRAFT_START;
        if (cell >= 0 && cell < MenuKnowledgeInscriber.CRAFT_SLOTS && clickType == ClickType.PICKUP) {
            ItemStack carried = menu.getCarried();
            grid.setCell(cell, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return true;
        }
        int pattern = slotId - MenuKnowledgeInscriber.IDX_PATTERN_START;
        if (pattern >= 0 && pattern < MenuKnowledgeInscriber.PATTERN_SLOTS) {
            grid.loadPattern(pattern);
            return true;
        }
        return false;
    }

    /** Applies one cell from {@code InscriberGridPayload}: the write lands on the machine's container. */
    static void setCell(MenuKnowledgeInscriber menu, int cell, ItemStack stack) {
        if (menu.inscriber == null) {
            return;
        }
        menu.inscriber.setGridCell(cell, stack);
        menu.broadcastChanges();
    }

    /**
     * Applies a whole grid from {@code InscriberGridFillPayload}, in one write and one resolution, so
     * the two sides change together rather than a cell at a time; missing entries are treated as empty.
     */
    static void fill(MenuKnowledgeInscriber menu, List<ItemStack> cells, int count) {
        if (menu.inscriber == null) {
            return;
        }
        List<ItemStack> full = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            full.add(i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
        }
        menu.inscriber.setGrid(full);
        // The client's own copy was already written; the data slots have to catch up this tick.
        menu.broadcastChanges();
    }
}
