package thaumicenergistics_ce.menu;

import appeng.api.upgrades.Upgrades;
import appeng.menu.SlotSemantics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.item.ItemEssentiaCell;

/**
 * 在存储元件工作台菜单里，被 shift 点击的物品堆去往何处，以及它去往的范围。
 * 玩家侧是快捷栏与主物品栏合在一起，因为 AE2 按自己的语义添加快捷栏，
 * 这会把第一个主物品栏槽位向后挪九个槽位。升级卡只有在元件在位时
 * 才能放入：卡是搭乘在元件上的。
 */
final class CellWorkbenchShiftClick {

    /** 给 {@code moveItemStackTo} 的一次移动：要填充的范围，以及先从哪一端填。 */
    record Move(int from, int to, boolean reverse) {}

    private final MenuEssentiaCellWorkbench menu;

    private final int playerStart;

    private final int playerEnd;

    private final int cardStart;

    private final int cardEnd;

    CellWorkbenchShiftClick(MenuEssentiaCellWorkbench menu) {
        this.menu = menu;
        List<Slot> playerSide = new ArrayList<>(menu.getSlots(SlotSemantics.PLAYER_HOTBAR));
        playerSide.addAll(menu.getSlots(SlotSemantics.PLAYER_INVENTORY));
        int[] playerRange = slotRange(playerSide, menu.slots.size());
        this.playerStart = playerRange[0];
        this.playerEnd = playerRange[1];
        int[] cardRange = slotRange(menu.getSlots(SlotSemantics.UPGRADE), menu.slots.size());
        this.cardStart = cardRange[0];
        this.cardEnd = cardRange[1];
    }

    /**
     * 把一组槽位表示成 {@code moveItemStackTo} 所需的单个范围：最小索引与最大索引
     * 加一；空组会变成末尾的一个空范围，所以移入它只会失败。
     */
    private static int[] slotRange(List<Slot> group, int slotCount) {
        int start = Integer.MAX_VALUE;
        int end = 0;
        for (Slot slot : group) {
            start = Math.min(start, slot.index);
            end = Math.max(end, slot.index + 1);
        }
        return end == 0 ? new int[] {slotCount, slotCount} : new int[] {start, end};
    }

    /** 在 {@code slot} 上 shift 点击所表示的移动；物品堆原地不动时为 null。 */
    @Nullable Move moveFor(Slot slot, int index, ItemStack stack) {
        Slot cell = menu.cellSlot();
        if (slot == cell) {
            return new Move(playerStart, playerEnd, true);
        }
        if (index >= playerStart) {
            if (stack.getItem() instanceof ItemEssentiaCell && !cell.hasItem()) {
                // 目标范围是元件槽位，而不是被点击的那个：被点击槽位自身的范围
                // 会把物品堆并进它自己，所以这个范围指的是物品堆要去的地方。
                return new Move(cell.index, cell.index + 1, false);
            }
            if (menu.hasCellInMenu() && Upgrades.isUpgradeCardItem(stack)) {
                // 升级卡搭乘在元件上，所以没有元件就无处可放。元件接受哪些卡
                // 由元件自己的升级物品栏决定，通过槽位的 mayPlace 询问。
                return new Move(cardStart, cardEnd, false);
            }
            return null;
        }
        if (menu.getSlots(SlotSemantics.UPGRADE).contains(slot)) {
            return new Move(playerStart, playerEnd, true);
        }
        // 凹槽：标记是一个类型而不是一堆数量，所以没有东西可供 shift 点击移动。
        return null;
    }
}
