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
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingInput;
import thaumicenergistics_ce.arcane.TerminalArcaneCraftingStore;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.util.ThELog;

public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    private final @Nullable ServerPlayer serverPlayer;
    private final @Nullable PartArcaneCraftingTerminal part;

    private final thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal ownerMenu;

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
        return false;
    }

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
        var result = ArcaneCraftingTransaction.preview(workbenchContext(serverPlayer), serverPlayer, input);
        lastFailure = result.failure();
        if (!result.successful()) {
            ThELog.LOG.info("[arcane] no craft offered for the grid: {}", result.failure());
        }
        setDisplayedCraftingOutput(result.successful() ? result.output() : ItemStack.EMPTY);
        ownerMenu.sendCraftCost(result.successful() ? result.cost() : null);
    }

    public ArcaneCraftingTransaction.Failure lastFailure() {
        return lastFailure;
    }

    private ArcaneCraftingTransaction.Result craftCommitted(
            ServerPlayer server, IArcaneCraftingInput input, TerminalArcaneCraftingStore store) {
        try (Transaction transaction = Transaction.openRoot()) {
            var result = ArcaneCraftingTransaction.craft(workbenchContext(server), server, input, store, transaction);
            if (result.successful()) {
                transaction.commit();
            }
            return result;
        }
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
            var store = new TerminalArcaneCraftingStore(
                    terminal.craftingGrid(), terminal.crystalInventory(), terminal.wandInventory(), who);
            var result = craftCommitted(server, input, store);
            if (!result.successful()) {
                ThELog.LOG.info(
                        "[arcane] craft click refused: successful={} failure={}",
                        result.successful(), result.failure());
                break;
            }

            ItemStack output = result.output().copy();
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
        who.drop(output.copy(), false);
        return false;
    }

    private @Nullable IArcaneCraftingInput buildInput() {
        if (part == null) {
            return null;
        }
        List<ItemStack> cells = IntStream.range(0, PartArcaneCraftingTerminal.GRID_SIZE)
                .mapToObj(i -> part.craftingGrid().getStackInSlot(i))
                .toList();
        ItemStack wand = part.wandInventory().getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT);
        List<ItemStack> crystals = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            crystals.add(part.crystalInventory().getStackInSlot(i));
        }
        boolean visConnection = ownerMenu.hasVisConnectionCard();
        return new TerminalArcaneCraftingInput(
                cells, serverPlayer, wand, crystals, part, ownerMenu.auraPayer(), visConnection)
                .withVisCard(ownerMenu.visCard());
    }

    private ArcaneWorkbenchContext workbenchContext(ServerPlayer server) {
        return ArcaneWorkbenchContext.virtual(
                server, PartArcaneCraftingTerminal.CONTEXT_HOST, server.getUUID());
    }
}
