package thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingInput;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingInput;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingStore;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

/**
 * The Arcane Crafting Terminal's result slot.
 * <ul>
 *   <li>Extends {@code CraftingTermSlot} because {@code doClick}, the craft entry point, is declared there.
 *   <li>{@code ArcaneCraftingTransaction} matches and charges; {@link #refresh} previews without paying.
 *   <li>The payment is the terminal's own grid, crystal and wand slots, as on Thaumaturge's workbench:
 *       a recipe only matches when the grid holds its ingredients, so the network is never asked for
 *       them - see {@link TerminalArcaneCraftingStore}.
 * </ul>
 */
public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    private final @Nullable ServerPlayer serverPlayer;
    private final @Nullable PartArcaneCraftingTerminal part;

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
        this.ownerMenu = ownerMenu;
    }

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
            ThELog.LOG.info("[arcane] no craft offered for the grid: {}", result.failure());
        }
        setDisplayedCraftingOutput(result.successful() ? result.output() : ItemStack.EMPTY);
        // Sent with its result, so the screen cannot draw a cost for a grid that changed.
        ownerMenu.sendCraftCost(result.successful() ? result.cost() : null);
    }

    public ArcaneCraftingTransaction.Failure lastFailure() {
        return lastFailure;
    }

    @Override
    public void doClick(InventoryAction action, Player who) {
        PartArcaneCraftingTerminal terminal = part;
        if (terminal == null || !(who instanceof ServerPlayer server)) {
            return;
        }
        int attempts = switch (action) {
            case CRAFT_SHIFT, CRAFT_ALL -> 64;
            default -> 1;
        };

        for (int i = 0; i < attempts; i++) {
            IArcaneCraftingInput input = buildInput();
            if (input == null || input.isEmpty()) {
                break;
            }
            // The terminal's own containers pay, as on Thaumaturge's workbench: the grid, the crystal slots
            // and the wand. Charging the network for the ingredients as well would ask for a second copy of
            // what the player has already arranged, which is what refused a craft the grid could afford.
            var store = new TerminalArcaneCraftingStore(
                    terminal.craftingGrid(), terminal.crystalInventory(), terminal.wandInventory(), who);
            var result = ArcaneCraftingTransaction.craft(workbenchContext(), server, input, store, false);
            if (!result.successful()) {
                ThELog.LOG.info(
                        "[arcane] craft click refused: successful={} failure={}",
                        result.successful(), result.failure());
                break;
            }

            // The grid, the crystals and the wand are already charged by the store; the remainders went
            // back to their own cells with it.
            ItemStack output = result.output().copy();
            // Read before handing over: Inventory#add sets the count to what did NOT fit.
            String produced = output.toString();
            boolean placed = deliver(output, action, who);
            ThELog.LOG.info(
                    "[arcane] craft click committed: produced={} placed={} cost={}",
                    produced,
                    placed,
                    result.cost());
            if (!placed) {
                break;
            }
            refresh();
        }
    }

    /** A plain click puts the product on the cursor, a bulk craft fills the inventory; the commit has
     * already taken payment.
     *
     * @param action the gesture, so a shift-click still fills the inventory as a player expects
     * @return whether the product went to the cursor or inventory; {@code false} when it did not fit */
    private boolean deliver(ItemStack output, InventoryAction action, Player who) {
        if (output.isEmpty()) {
            return true;
        }
        boolean bulk = action == InventoryAction.CRAFT_SHIFT || action == InventoryAction.CRAFT_ALL;
        if (!bulk) {
            var carried = this.getMenu().getCarried();
            if (carried.isEmpty()) {
                this.getMenu().setCarried(output);
                return true;
            }
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
        // The card is read once, here: both aura passes of one craft then read this frozen answer instead
        // of asking the slot again, which is what keeps the commit from disagreeing with the simulation.
        boolean visConnection = ownerMenu.hasVisConnectionCard();
        return new TerminalArcaneCraftingInput(
                cells, serverPlayer, wand, crystals, part, ownerMenu.auraPayer(), visConnection);
    }

    /** A virtual workbench owned by this machine and player: a terminal on a cable has no block to point
     * at. */
    private ArcaneWorkbenchContext workbenchContext() {
        return ArcaneWorkbenchContext.virtual(
                serverPlayer, PartArcaneCraftingTerminal.CONTEXT_HOST, serverPlayer.getUUID());
    }
}
