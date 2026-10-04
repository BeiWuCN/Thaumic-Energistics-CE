package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingInput;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.arcane.EssentiaCrystals;
import thaumicenergistics_ce.arcane.NetworkArcaneCraftingStore;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingInput;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The Arcane Crafting Terminal's result slot.
 * <ul>
 *   <li>Extends {@code CraftingTermSlot} because {@code doClick}, the craft entry point, is declared there.</li>
 *   <li>{@code ArcaneCraftingTransaction} matches and charges; only {@code craft} charges, {@link #refresh} previews.</li>
 * </ul>
 */
public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    private final @Nullable ServerPlayer serverPlayer;
    private final @Nullable PartArcaneCraftingTerminal part;

    /** The three objects the parent was handed, kept because the arcane craft reaches the network itself. */
    private final MEStorage storage;
    private final IEnergySource energySource;
    private final IActionSource actionSource;

    /** Concrete rather than {@link ICraftingGridMenu}: sending the vis cost to the screen needs the menu. */
    private final thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal ownerMenu;

    /** Why the last {@link #refresh()} offered nothing, or {@code NONE}; the self-test reads it. */
    private ArcaneCraftingTransaction.Failure lastFailure = ArcaneCraftingTransaction.Failure.NONE;

    public ArcaneCraftingResultSlot(
            Player player,
            IActionSource actionSource,
            IEnergySource energySource,
            MEStorage storage,
            InternalInventory craftingGrid,
            InternalInventory resultInventory,
            thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal ownerMenu,
            @Nullable PartArcaneCraftingTerminal part) {
        super(player, actionSource, energySource, storage, craftingGrid, resultInventory, ownerMenu);
        this.serverPlayer = player instanceof ServerPlayer server ? server : null;
        this.part = part;
        this.storage = storage;
        this.energySource = energySource;
        this.actionSource = actionSource;
        this.ownerMenu = ownerMenu;
    }

    /** Preview only: writes what the grid would produce, and consumes nothing. */
    @Override
    public boolean mayPickup(Player player) {
        // Every take goes through doClick, which charges; vanilla must not hand the output out first.
        return false;
    }

    /** Recomputed on a change, not per frame: a full recipe match plus a cost calculation. */
    public void refresh() {
        if (part == null || serverPlayer == null) {
            return;
        }
        IArcaneCraftingInput input = buildInput();
        if (input == null) {
            setDisplayedCraftingOutput(ItemStack.EMPTY);
            ownerMenu.sendCraftCost(null);
            lastFailure = ArcaneCraftingTransaction.Failure.NONE;
            return;
        }
        var result = ArcaneCraftingTransaction.preview(workbenchContext(), serverPlayer, input);
        lastFailure = result.failure();
        if (!result.successful()) {
            // The transaction knows which check refused a grid that looks right.
            ThaumicEnergistics.LOG.info("[arcane] no craft offered for the grid: {}", result.failure());
        }
        setDisplayedCraftingOutput(result.successful() ? result.output() : ItemStack.EMPTY);
        // Sent with its result, so the screen cannot draw a cost for a grid that changed.
        ownerMenu.sendCraftCost(result.successful() ? result.cost() : null);
    }

    /** Why the grid offers no craft, or {@code NONE} when it does. For diagnostics, not display. */
    public ArcaneCraftingTransaction.Failure lastFailure() {
        return lastFailure;
    }

    /** Performs the craft; every action that means "craft" arrives here, a plain click included. */
    @Override
    public void doClick(InventoryAction action, Player who) {
        if (part == null || !(who instanceof ServerPlayer server)) {
            return;
        }
        // A full stack for shift / craft-all; the loop ends as soon as the grid stops matching.
        int attempts = switch (action) {
            case CRAFT_SHIFT, CRAFT_ALL -> 64;
            default -> 1;
        };

        for (int i = 0; i < attempts; i++) {
            IArcaneCraftingInput input = buildInput();
            if (input == null || input.isEmpty()) {
                break;
            }
            var store = new NetworkArcaneCraftingStore(storage, energySource, actionSource);
            var result = ArcaneCraftingTransaction.craft(workbenchContext(), server, input, store, false);
            if (!result.successful()) {
                // Payment comes later, so a refusal here costs the player nothing.
                ThaumicEnergistics.LOG.info(
                        "[arcane] craft click refused: successful={} failure={}",
                        result.successful(), result.failure());
                break;
            }

            // The grid is the template, NOT consumed - the ME network pays the ingredients.
            settleRemainders(result.remainders(), who);
            consumeCrystals(result.cost());

            ItemStack output = result.output().copy();
            // Read before handing over: Inventory#add sets the count to what did NOT fit.
            String produced = output.toString();
            boolean placed = deliver(output, action, who);
            ThaumicEnergistics.LOG.info(
                    "[arcane] craft click committed: produced={} placed={} cost={}",
                    produced,
                    placed,
                    result.cost());
            if (!placed) {
                // Nowhere to put it, so it went on the floor; stop rather than repeat for a bulk craft.
                break;
            }
            // The grid changed, so the recipe may no longer match; an empty result ends the loop.
            refresh();
        }
    }

    /** A plain click puts the product on the cursor, a bulk craft fills the inventory. Payment is already
     * taken by the commit, so a product never handed over reads as broken.
     *
     * @param action the gesture, so a shift-click still fills the inventory as a player expects
     * @return whether the product went to the cursor or inventory; {@code false} when it did not fit */
    private boolean deliver(ItemStack output, InventoryAction action, Player who) {
        if (output.isEmpty()) {
            // Rare and not an error: a recipe may legitimately assemble to air.
            return true;
        }
        boolean bulk = action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL;
        if (!bulk) {
            var carried = this.getMenu().getCarried();
            if (carried.isEmpty()) {
                this.getMenu().setCarried(output);
                return true;
            }
            // Holding something: stack onto it when it matches, else fall through.
            if (ItemStack.isSameItemSameComponents(carried, output)
                    && carried.getCount() + output.getCount() <= carried.getMaxStackSize()) {
                carried.grow(output.getCount());
                return true;
            }
        }
        if (who.getInventory().add(output)) {
            return true;
        }
        // Payment is already taken, so the product exists either way; the caller reads this as "stop".
        who.drop(output.copy(), false);
        return false;
    }

    /** Takes {@code crystalsNeeded} - what the wand could not cover and the network store ignores - out of the
     * six crystal slots, by aspect, clamped to what each stack holds. */
    private void consumeCrystals(@Nullable ArcaneCraftCost cost) {
        if (cost == null || part == null) {
            return;
        }
        AspectList needed = cost.crystalsNeeded();
        if (needed.isEmpty()) {
            return;
        }
        // Aspects outer: one aspect can sit in several slots, and a slot pass would take the full requirement
        // from each.
        for (Holder<IAspect> aspect : needed.aspects()) {
            int outstanding = needed.amountOf(aspect);
            for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS && outstanding > 0; i++) {
                ItemStack stack = part.crystalInventory().getStackInSlot(i);
                Holder<IAspect> carried = EssentiaCrystals.aspectOf(stack);
                if (carried == null || !carried.equals(aspect)) {
                    continue;
                }
                int take = Math.min(outstanding, stack.getCount());
                stack.shrink(take);
                outstanding -= take;
            }
        }
    }

    /** Builds the input for the current grid, or {@code null} when there is nothing to match. */
    private @Nullable IArcaneCraftingInput buildInput() {
        if (part == null) {
            return null;
        }
        // Nine cells, empty ones included: Thaumaturge indexes a grid as nine whatever it holds, so the
        // list must not be trimmed - see TerminalArcaneCraftingInput.
        List<ItemStack> cells = IntStream.range(0, PartArcaneCraftingTerminal.GRID_SIZE)
                .mapToObj(i -> part.craftingGrid().getStackInSlot(i))
                .toList();
        ItemStack wand = part.wandInventory().getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT);
        // Crystals come from their own slots, never the grid: a crystal in a grid cell is an
        // ingredient to the match, and counting it as payment too would make the two disagree.
        List<ItemStack> crystals = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            crystals.add(part.crystalInventory().getStackInSlot(i));
        }
        return new TerminalArcaneCraftingInput(cells, serverPlayer, wand, crystals, part);
    }

    /** Hands the player whatever the recipe kept and leaves the grid alone: the network pays, so consuming the
     * grid too would charge twice. {@code remainders()} is indexed by grid slot, so a catalyst comes back here. */
    private void settleRemainders(List<ItemStack> remainders, Player who) {
        for (ItemStack keeps : remainders) {
            if (keeps.isEmpty()) {
                continue;
            }
            // Handed over as a copy: Inventory#add consumes what it is given.
            if (!who.getInventory().add(keeps.copy())) {
                who.drop(keeps.copy(), false);
            }
        }
    }

    /** A virtual workbench owned by this machine and player: a terminal on a cable has no block to point at. */
    private ArcaneWorkbenchContext workbenchContext() {
        return ArcaneWorkbenchContext.virtual(
                serverPlayer, PartArcaneCraftingTerminal.CONTEXT_HOST, serverPlayer.getUUID());
    }
}
