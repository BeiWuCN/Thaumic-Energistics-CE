package thaumicenergistics_ce.arcane;

import appeng.api.inventories.InternalInventory;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The terminal's own three containers, presented to Thaumaturge as the store an arcane craft works on.
 * <ul>
 *   <li>The grid is the payment, as on Thaumaturge's workbench: one item leaves each occupied cell, the
 *       remainder takes that cell's place, the crystals leave the crystal slots, and the wand goes back
 *       in as the craft leaves it. A recipe only matches when the grid already holds its ingredients, so
 *       charging the network for them too would ask for a second copy of what the player placed.
 *   <li>{@link #consume} runs twice, once simulated and once for real: only the second takes anything,
 *       which is what makes a refused craft cost nothing.
 *   <li>The grid is the terminal's, not the network's: a cell holds what the player is arranging right
 *       now, and the network is never asked for it.
 * </ul>
 */
public final class TerminalArcaneCraftingStore implements IArcaneCraftingStore {

    private final InternalInventory grid;
    private final InternalInventory crystals;
    private final InternalInventory wand;
    private final Player player;

    public TerminalArcaneCraftingStore(
            InternalInventory grid, InternalInventory crystals, InternalInventory wand, Player player) {
        this.grid = grid;
        this.crystals = crystals;
        this.wand = wand;
        this.player = player;
    }

    /**
     * Checks that the grid still holds what the craft matched and that the crystal slots cover the
     * crystals it wants, then charges it: one item out of every cell, each remainder into its own cell,
     * the crystals out of their slots, and {@code consumption.wand()} into the wand slot.
     *
     * @param consumption what one craft uses up
     * @param simulate    true to only check the grid against {@link Consumption#grid()} and the crystals
     * @return whether the containers still matched (and, when not simulating, the change was applied)
     */
    @Override
    public boolean consume(Consumption consumption, boolean simulate) {
        if (grid == null || crystals == null || wand == null) {
            return false;
        }
        if (!matches(consumption.grid()) || !hasCrystals(consumption.crystals())) {
            return false;
        }
        if (simulate) {
            return true;
        }
        List<ItemStack> remainders = consumption.remainders();
        for (int slot = 0; slot < PartArcaneCraftingTerminal.GRID_SIZE; slot++) {
            grid.extractItem(slot, 1, false);
            placeRemainder(slot, slot < remainders.size() ? remainders.get(slot) : ItemStack.EMPTY);
        }
        consumeCrystals(consumption.crystals());
        if (!consumption.wand().isEmpty()) {
            wand.setItemDirect(PartArcaneCraftingTerminal.WAND_SLOT, consumption.wand());
        }
        return true;
    }

    /** Cell for cell: the consumption's grid is the nine cells flattened {@code x + y * 3}, the order the
     * slots are read in. A count change is a mismatch too, so a grid taken from under the craft refuses. */
    private boolean matches(List<ItemStack> expected) {
        if (expected.size() != PartArcaneCraftingTerminal.GRID_SIZE) {
            return false;
        }
        for (int slot = 0; slot < PartArcaneCraftingTerminal.GRID_SIZE; slot++) {
            if (!ItemStack.matches(grid.getStackInSlot(slot), expected.get(slot))) {
                return false;
            }
        }
        return true;
    }

    private boolean hasCrystals(AspectList needed) {
        for (Holder<IAspect> aspect : needed.aspects()) {
            int found = 0;
            for (int slot = 0; slot < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; slot++) {
                Holder<IAspect> carried = EssentiaCrystals.aspectOf(crystals.getStackInSlot(slot));
                if (carried != null && carried.equals(aspect)) {
                    found += crystals.getStackInSlot(slot).getCount();
                }
            }
            if (found < needed.amountOf(aspect)) {
                return false;
            }
        }
        return true;
    }

    /** Aspects outer: one aspect can sit in several slots, and a slot pass would take the full
     * requirement from each. */
    private void consumeCrystals(AspectList needed) {
        for (Holder<IAspect> aspect : needed.aspects()) {
            int outstanding = needed.amountOf(aspect);
            for (int slot = 0; slot < PartArcaneCraftingTerminal.CRYSTAL_SLOTS && outstanding > 0; slot++) {
                ItemStack crystal = crystals.getStackInSlot(slot);
                Holder<IAspect> carried = EssentiaCrystals.aspectOf(crystal);
                if (carried == null || !carried.equals(aspect)) {
                    continue;
                }
                int take = Math.min(outstanding, crystal.getCount());
                // Shrunk in place: the inventory hands out the stack it holds, as on Thaumaturge's bench.
                crystal.shrink(take);
                outstanding -= take;
            }
        }
    }

    /** The remainder belongs to the cell it came out of: into an emptied cell, on top of the same item
     * when the cell still holds some, and to the player only when neither is possible. */
    private void placeRemainder(int slot, ItemStack remainder) {
        if (remainder.isEmpty()) {
            return;
        }
        ItemStack existing = grid.getStackInSlot(slot);
        if (existing.isEmpty()) {
            grid.setItemDirect(slot, remainder);
            return;
        }
        if (ItemStack.isSameItemSameComponents(existing, remainder)) {
            int room = existing.getMaxStackSize() - existing.getCount();
            int moved = Math.min(room, remainder.getCount());
            existing.grow(moved);
            remainder.shrink(moved);
            if (remainder.isEmpty()) {
                return;
            }
        }
        if (!player.getInventory().add(remainder)) {
            player.drop(remainder, false);
        }
    }
}
