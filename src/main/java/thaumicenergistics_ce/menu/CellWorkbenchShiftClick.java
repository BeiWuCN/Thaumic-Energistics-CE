package thaumicenergistics_ce.menu;

import appeng.api.upgrades.Upgrades;
import appeng.menu.SlotSemantics;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.item.ItemEssentiaCell;

final class CellWorkbenchShiftClick {

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

    private static int[] slotRange(List<Slot> group, int slotCount) {
        int start = Integer.MAX_VALUE;
        int end = 0;
        for (Slot slot : group) {
            start = Math.min(start, slot.index);
            end = Math.max(end, slot.index + 1);
        }
        return end == 0 ? new int[] {slotCount, slotCount} : new int[] {start, end};
    }

    @Nullable Move moveFor(Slot slot, int index, ItemStack stack) {
        Slot cell = menu.cellSlot();
        if (slot == cell) {
            return new Move(playerStart, playerEnd, true);
        }
        if (index >= playerStart) {
            if (stack.getItem() instanceof ItemEssentiaCell && !cell.hasItem()) {
                return new Move(cell.index, cell.index + 1, false);
            }
            if (menu.hasCellInMenu() && Upgrades.isUpgradeCardItem(stack)) {
                return new Move(cardStart, cardEnd, false);
            }
            return null;
        }
        if (menu.getSlots(SlotSemantics.UPGRADE).contains(slot)) {
            return new Move(playerStart, playerEnd, true);
        }
        return null;
    }
}
