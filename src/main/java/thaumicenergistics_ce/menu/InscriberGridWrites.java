package thaumicenergistics_ce.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

final class InscriberGridWrites {

    private InscriberGridWrites() {}

    static boolean route(
            MenuKnowledgeInscriber menu, InscriberGridState grid, int slotId, ContainerInput clickType) {
        if (slotId < 0 || slotId >= menu.slots.size()) {
            return false;
        }
        int cell = slotId - MenuKnowledgeInscriber.IDX_CRAFT_START;
        if (cell >= 0 && cell < MenuKnowledgeInscriber.CRAFT_SLOTS && clickType == ContainerInput.PICKUP) {
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

    static void setCell(MenuKnowledgeInscriber menu, int cell, ItemStack stack) {
        if (menu.inscriber == null) {
            return;
        }
        menu.inscriber.setGridCell(cell, stack);
        menu.broadcastChanges();
    }

    static void fill(MenuKnowledgeInscriber menu, List<ItemStack> cells, int count) {
        if (menu.inscriber == null) {
            return;
        }
        List<ItemStack> full = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            full.add(i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
        }
        menu.inscriber.setGrid(full);
        menu.broadcastChanges();
    }
}
