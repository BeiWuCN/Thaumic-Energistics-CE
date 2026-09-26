package thaumicenergistics.menu.slot;

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
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.arcane.EssentiaCrystals;
import thaumicenergistics.arcane.NetworkArcaneCraftingStore;
import thaumicenergistics.arcane.TerminalArcaneCraftingInput;
import thaumicenergistics.part.PartArcaneCraftingTerminal;

/**
 * The Arcane Crafting Terminal's result slot.
 *
 * <p>Extends AE2's {@code CraftingTermSlot} because {@code doClick} - the method AE2 calls to run a craft - is
 * declared there and not on the plain crafting slot; a slot that did not extend it would be clicked like an
 * output and never receive a craft action. AE2's own craft is replaced, since it runs a vanilla recipe.
 *
 * <p>The real work is Thaumaturge's: {@code ArcaneCraftingTransaction} matches the recipe and charges the vis
 * and crystal cost, and the terminal supplies the grid, the wand and a view of the ME network to pay from.
 * Only the commit path charges - {@link #refresh} previews.
 */
public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    private final @Nullable ServerPlayer serverPlayer;
    private final @Nullable PartArcaneCraftingTerminal part;

    /**
     * The same three objects the parent was handed, kept because the arcane craft has to reach the network
     * itself and the parent offers no accessor to pass on.
     */
    private final MEStorage storage;
    private final IEnergySource energySource;
    private final IActionSource actionSource;

    /** Concrete rather than {@link ICraftingGridMenu}: sending the vis cost to the screen is a menu operation
     * the interface does not expose. */
    private final thaumicenergistics.menu.MenuArcaneCraftingTerminal ownerMenu;

    /**
     * Why the last {@link #refresh()} offered nothing, or {@code NONE}. Exposed for the self-test, which has
     * to tell a part that cannot pay - it is not in a world, so it has no aura - from a grid that does not
     * resolve.
     */
    private ArcaneCraftingTransaction.Failure lastFailure = ArcaneCraftingTransaction.Failure.NONE;

    public ArcaneCraftingResultSlot(
            Player player,
            IActionSource actionSource,
            IEnergySource energySource,
            MEStorage storage,
            InternalInventory craftingGrid,
            InternalInventory resultInventory,
            thaumicenergistics.menu.MenuArcaneCraftingTerminal ownerMenu,
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
        // Every take goes through doClick, where the charge happens; vanilla must not hand out the output first.
        return false;
    }

    /** Recomputes the displayed output from the current grid. A full recipe match plus a cost calculation, so
     * it runs on a change rather than per frame. */
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
            // Worth logging: the transaction knows which check refused a grid that looks right.
            ThaumicEnergistics.LOG.info("[arcane] no craft offered for the grid: {}", result.failure());
        }
        setDisplayedCraftingOutput(result.successful() ? result.output() : ItemStack.EMPTY);
        // Sent with the result it belongs to, so the screen cannot draw a figure for a grid that changed.
        ownerMenu.sendCraftCost(result.successful() ? result.cost() : null);
    }

    /** Why the grid offers no craft, or {@code NONE} when it does. For diagnostics, not for display. */
    public ArcaneCraftingTransaction.Failure lastFailure() {
        return lastFailure;
    }

    /** Performs the craft. Every action that means "craft" arrives here, a plain click included; an arcane
     * craft has no vanilla path, so there is one route. */
    @Override
    public void doClick(InventoryAction action, Player who) {
        if (part == null || !(who instanceof ServerPlayer server)) {
            return;
        }
        // A full stack for shift / craft-all; the loop stops as soon as the grid stops matching.
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
            var result = ArcaneCraftingTransaction.commit(workbenchContext(), server, input, store);
            if (!result.successful() || !result.committed()) {
                // The one case where the player sees nothing happen at all. Payment is taken later in this
                // method, so a refusal here costs them nothing.
                ThaumicEnergistics.LOG.info(
                        "[arcane] craft click refused: successful={} committed={} failure={}",
                        result.successful(),
                        result.committed(),
                        result.failure());
                break;
            }

            // The grid is the recipe's template and is deliberately NOT consumed - the ingredients are paid
            // for out of the ME network by NetworkArcaneCraftingStore. Consuming it too charged the player
            // twice and emptied the template, which is why a shift-click could only ever craft once. See
            // settleRemainders.
            settleRemainders(result.remainders(), who);
            consumeCrystals(result.cost());

            ItemStack output = result.output().copy();
            // Described before it is handed over: Inventory#add consumes the stack and sets its count to
            // what would not fit, so logging afterwards printed "0 minecraft:air" for a craft that worked.
            String produced = output.toString();
            boolean placed = deliver(output, action, who);
            ThaumicEnergistics.LOG.info(
                    "[arcane] craft click committed: produced={} placed={} cost={}",
                    produced,
                    placed,
                    result.cost());
            if (!placed) {
                // Nowhere to put it, so it went on the floor; stop rather than repeat that for a bulk craft.
                break;
            }
            // The grid changed, so the recipe may no longer match; an empty result ends the loop.
            refresh();
        }
    }

    /**
     * A plain click puts the product on the cursor; only a bulk craft fills the inventory. Payment is taken
     * during the commit, before anything is handed over, so a craft with no visible product reads as broken
     * even though it worked - which is how this was reported.
     *
     * @param action the gesture, so a shift-click can still fill the inventory as a player expects
     * @return {@code true} when the product went to the cursor or the inventory, {@code false} when the player
     *     had no room for it and it was dropped instead
     */
    private boolean deliver(ItemStack output, InventoryAction action, Player who) {
        if (output.isEmpty()) {
            // The recipe produced nothing. Rare, and not an error: a recipe may legitimately assemble to air.
            return true;
        }
        boolean bulk = action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL;
        if (!bulk) {
            var carried = this.getMenu().getCarried();
            if (carried.isEmpty()) {
                this.getMenu().setCarried(output);
                return true;
            }
            // Holding something: stack onto it when it matches, else fall through rather than replace it.
            if (ItemStack.isSameItemSameComponents(carried, output)
                    && carried.getCount() + output.getCount() <= carried.getMaxStackSize()) {
                carried.grow(output.getCount());
                return true;
            }
        }
        if (who.getInventory().add(output)) {
            return true;
        }
        // Nowhere to put it. The payment is already taken, so the product exists either way; the caller
        // treats this as "stop".
        who.drop(output.copy(), false);
        return false;
    }

    /**
     * Takes the crystal cost out of the six crystal slots - {@code crystalsNeeded} is what the payment planner
     * could not cover from the wand, and the network store ignores it deliberately, so nothing else will.
     *
     * <p>By aspect rather than by slot, and clamped to what the stack holds.
     */
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
        // Nine cells, empty ones included. Thaumaturge indexes a grid as nine whatever it holds, so the
        // list must not be trimmed to what happens to hold something - see TerminalArcaneCraftingInput.
        List<ItemStack> cells = IntStream.range(0, PartArcaneCraftingTerminal.GRID_SIZE)
                .mapToObj(i -> part.craftingGrid().getStackInSlot(i))
                .toList();
        ItemStack wand = part.wandInventory().getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT);
        // Crystals come from their own slots, never the grid: a crystal in a grid cell is an ingredient as far
        // as the match is concerned, and counting it as payment too would make the two disagree.
        List<ItemStack> crystals = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            crystals.add(part.crystalInventory().getStackInSlot(i));
        }
        return new TerminalArcaneCraftingInput(cells, serverPlayer, wand, crystals, part);
    }

    /**
     * Hands the player whatever the recipe kept, and leaves the grid alone: the ingredients are paid for out of
     * the ME network, so consuming the grid too charged the player twice and emptied the template - which is
     * why a shift-click could only ever craft once.
     *
     * <p>{@code remainders()} is indexed by grid slot; a catalyst survives a craft this way, and what comes
     * back goes to the player rather than into the grid.
     */
    private void settleRemainders(List<ItemStack> remainders, Player who) {
        for (ItemStack keeps : remainders) {
            if (keeps.isEmpty()) {
                continue;
            }
            // Handed over as a copy: Inventory#add consumes whatever it is given.
            if (!who.getInventory().add(keeps.copy())) {
                who.drop(keeps.copy(), false);
            }
        }
    }

    /** A virtual workbench owned by this machine and this player: a terminal on a cable has no workbench block
     * to point at. */
    private ArcaneWorkbenchContext workbenchContext() {
        return ArcaneWorkbenchContext.virtual(
                serverPlayer, PartArcaneCraftingTerminal.CONTEXT_HOST, serverPlayer.getUUID());
    }
}
