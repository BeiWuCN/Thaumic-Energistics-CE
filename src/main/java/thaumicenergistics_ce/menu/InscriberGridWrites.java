package thaumicenergistics_ce.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/**
 * 一次点击或一个载荷写进 3x3 网格的内容：格位接收手持物品堆，
 * 凹槽加载它持有的样板，两个载荷分别传一个格位或整个网格。
 * 载荷那一半只在服务端跑，客户端写的容器服务端永远看不到。
 */
final class InscriberGridWrites {

    private InscriberGridWrites() {}

    /**
     * 分发落在合成网格或样板凹槽上的一次点击。已处理时返回 true，
     * 调用方就不能再把它交给原版的槽位逻辑。
     */
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

    /** 应用 {@code InscriberGridPayload} 送来的一个格位：写入落在机器的容器上。 */
    static void setCell(MenuKnowledgeInscriber menu, int cell, ItemStack stack) {
        if (menu.inscriber == null) {
            return;
        }
        menu.inscriber.setGridCell(cell, stack);
        menu.broadcastChanges();
    }

    /**
     * 应用 {@code InscriberGridFillPayload} 送来的整个网格，一次写入一次解析，
     * 两侧一起变化，不逐格来；缺失的条目按空处理。
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
        // 客户端自己的副本已经写入；数据槽位要在这个 tick 内跟上。
        menu.broadcastChanges();
    }
}
