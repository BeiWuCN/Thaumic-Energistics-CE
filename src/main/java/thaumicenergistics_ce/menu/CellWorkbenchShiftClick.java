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
 * 存储元件工作台菜单里，shift 点击的物品堆去往哪里、范围多大。
 * 玩家侧把快捷栏和主物品栏合为一段。
 * AE2 按自己的语义添加快捷栏，第一个主物品栏槽位因此后移九个。
 * 升级卡只在元件在位时放得进去，卡搭乘在元件上。
 */
final class CellWorkbenchShiftClick {

    /** 给 {@code moveItemStackTo} 的一次移动：范围，以及先从哪一端填。 */
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
     * 把一组槽位压成 {@code moveItemStackTo} 要的单个范围：最小索引到最大索引加一。
     * 空组得到末尾的空范围，移进去必然失败。
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

    /** 在 {@code slot} 上 shift 点击表示的移动；物品堆原地不动时为 null。 */
    @Nullable Move moveFor(Slot slot, int index, ItemStack stack) {
        Slot cell = menu.cellSlot();
        if (slot == cell) {
            return new Move(playerStart, playerEnd, true);
        }
        if (index >= playerStart) {
            if (stack.getItem() instanceof ItemEssentiaCell && !cell.hasItem()) {
                // 目标是元件槽位，不是被点击的那个。
                // 指向被点击槽位的话物品堆会并进它自己；这个范围就是物品堆的落点。
                return new Move(cell.index, cell.index + 1, false);
            }
            if (menu.hasCellInMenu() && Upgrades.isUpgradeCardItem(stack)) {
                // 升级卡搭乘在元件上，元件不在就没处放。
                // 元件收哪些卡由它自己的升级物品栏说了算，靠槽位的 mayPlace 问。
                return new Move(cardStart, cardEnd, false);
            }
            return null;
        }
        if (menu.getSlots(SlotSemantics.UPGRADE).contains(slot)) {
            return new Move(playerStart, playerEnd, true);
        }
        // 凹槽：标记是类型不是数量，没有物品堆可以 shift 点击搬走。
        return null;
    }
}
