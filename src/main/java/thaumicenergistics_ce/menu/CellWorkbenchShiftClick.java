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
 * Where a shift-clicked stack goes in the cell workbench menu, and the ranges it goes to.
 * The player side is the hotbar and the main inventory together, because AE2 adds the hotbar
 * under its own semantic, which shifts the first main-inventory slot nine slots along. A card
 * only goes in while a cell is there: the cards ride on the cell.
 */
final class CellWorkbenchShiftClick {

    /** A move for {@code moveItemStackTo}: the range to fill and which end of it to fill first. */
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
     * A group of slots as the one range {@code moveItemStackTo} wants: lowest index and one past the
     * highest; an empty group becomes an empty range at the end, so a move into it just fails.
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

    /** The move a shift-click on {@code slot} means, or null when the stack stays where it is. */
    @Nullable Move moveFor(Slot slot, int index, ItemStack stack) {
        Slot cell = menu.cellSlot();
        if (slot == cell) {
            return new Move(playerStart, playerEnd, true);
        }
        if (index >= playerStart) {
            if (stack.getItem() instanceof ItemEssentiaCell && !cell.hasItem()) {
                // The destination is the cell slot, not the clicked one: the clicked slot's own range
                // merged the stack into itself, so the range names where the stack is going.
                return new Move(cell.index, cell.index + 1, false);
            }
            if (menu.hasCellInMenu() && Upgrades.isUpgradeCardItem(stack)) {
                // A card rides on the cell, so there is nowhere to put one without it. Which cards the cell
                // takes is the cell's own upgrade inventory's call, asked through the slots' mayPlace.
                return new Move(cardStart, cardEnd, false);
            }
            return null;
        }
        if (menu.getSlots(SlotSemantics.UPGRADE).contains(slot)) {
            return new Move(playerStart, playerEnd, true);
        }
        // A well: a mark is a type, not a pile, so there is nothing for shift-click to move.
        return null;
    }
}
