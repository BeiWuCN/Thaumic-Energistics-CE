package com.beiwu.thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcArcanePayment;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.minecraft.world.item.crafting.CraftingInput;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingInput;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.TerminalArcaneCraftingInput;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.TerminalArcaneCraftingStore;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    private final @Nullable ServerPlayer serverPlayer;
    private final @Nullable PartArcaneCraftingTerminal part;

    private final com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal ownerMenu;

    private ArcaneCraftingTransaction.Failure lastFailure = ArcaneCraftingTransaction.Failure.NONE;

    public ArcaneCraftingResultSlot(
            Player player,
            IActionSource actionSource,
            IEnergySource energySource,
            MEStorage storage,
            InternalInventory craftingGrid,
            InternalInventory resultInventory,
            com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal ownerMenu,
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
        if (serverPlayer == null) {
            return;
        }
        if (!ownerMenu.connectionUnchanged()) {
            setDisplayedCraftingOutput(ItemStack.EMPTY);
            ownerMenu.clearCraftCost();
            return;
        }
        TerminalArcaneCraftingInput input = buildInput();
        if (input == null) {
            setDisplayedCraftingOutput(ItemStack.EMPTY);
            ownerMenu.clearCraftCost();
            lastFailure = ArcaneCraftingTransaction.Failure.NONE;
            return;
        }
        var result = ArcaneCraftingTransaction.preview(workbenchContext(serverPlayer), serverPlayer, input);
        lastFailure = result.failure();
        if (lastFailure == ArcaneCraftingTransaction.Failure.NO_RECIPE) {
            ItemStack output = vanillaOutput(serverPlayer);
            if (!output.isEmpty()) {
                lastFailure = ArcaneCraftingTransaction.Failure.NONE;
                setDisplayedCraftingOutput(output);
                ownerMenu.clearCraftCost();
                return;
            }
        }
        setDisplayedCraftingOutput(result.successful() ? result.output() : ItemStack.EMPTY);
        // 和结果一起发，屏幕就不会给已经变了的网格画开销。
        // 失败也照发：灵气、法杖要素或晶体不够时配方其实是匹配上的，
        // 这几行正该留着告诉玩家差多少，而不是整片消失。
        var cost = result.cost();
        ownerMenu.sendCraftCost(cost, auraDiscountPercent(input, cost));
    }

    private int auraDiscountPercent(TerminalArcaneCraftingInput input, @Nullable ArcaneCraftCost cost) {
        if (cost == null || cost.auraVis() <= 0 || serverPlayer == null) {
            return 0;
        }
        var inspection = ArcaneCraftingTransaction.inspect(workbenchContext(serverPlayer), serverPlayer, input);
        if (!inspection.successful() || inspection.recipeId() == null) {
            return 0;
        }
        var holder = serverPlayer.level().getServer().getRecipeManager().byKey(inspection.recipeId()).orElse(null);
        if (holder == null || !(holder.value() instanceof IArcaneRecipe recipe)) {
            return 0;
        }
        int undiscounted = TcArcanePayment.crudeCost(recipe);
        if (undiscounted <= 0) {
            return 0;
        }
        return Math.clamp(Math.round(100.0F * (undiscounted - cost.auraVis()) / undiscounted), 0, 100);
    }

    public boolean matchesTransferredRecipe(String recipeId) {
        return inspectTransferredRecipe(recipeId) != null;
    }

    public AspectList crystalsForTransfer(String recipeId) {
        var inspection = inspectTransferredRecipe(recipeId);
        if (inspection == null) {
            return AspectList.EMPTY;
        }
        if (ownerMenu.crystalFirst) {
            return inspection.requirements().crystals();
        }
        var cost = ArcaneCraftingTransaction.preview(workbenchContext(serverPlayer), serverPlayer, buildInput()).cost();
        return cost == null ? AspectList.EMPTY : cost.crystalsNeeded();
    }

    private ArcaneCraftingTransaction.@Nullable Inspection inspectTransferredRecipe(String recipeId) {
        var input = buildInput();
        if (serverPlayer == null || input == null) {
            return null;
        }
        var context = workbenchContext(serverPlayer);
        var inspection = ArcaneCraftingTransaction.inspect(context, serverPlayer, input);
        if (!inspection.successful() || inspection.recipeId() == null
                || !inspection.recipeId().identifier().toString().equals(recipeId)) {
            return null;
        }
        return inspection;
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
        if (!ownerMenu.connectionUnchanged() || !(who instanceof ServerPlayer server)) {
            return;
        }
        int attempts = switch (action) {
            case CRAFT_SHIFT, CRAFT_ALL -> 64;
            default -> 1;
        };

        for (int i = 0; i < attempts; i++) {
            TerminalArcaneCraftingInput input = buildInput();
            if (input == null || input.isEmpty()) {
                break;
            }
            var store = new TerminalArcaneCraftingStore(
                    ownerMenu.getCraftingMatrix(), ownerMenu.crystalInventory(), ownerMenu.wandInventory(), who,
                    input.left(), input.top(), input.width(), input.height());
            var result = craftCommitted(server, input, store);
            if (!result.successful()) {
                if (i == 0 && result.failure() == ArcaneCraftingTransaction.Failure.NO_RECIPE) {
                    ItemStack vanilla = vanillaOutput(server);
                    if (!vanilla.isEmpty()) {
                        setDisplayedCraftingOutput(vanilla);
                        super.doClick(action, who);
                        refresh();
                        return;
                    }
                }
                who.sendOverlayMessage(Component.translatable(
                        "gui.thaumicenergistics_ce.arcane_terminal.craft_failed",
                        Component.translatable(failureKey(result.failure()))));
                break;
            }

            ItemStack output = result.output().copy();
            boolean placed = deliver(output, action, who);
            if (!placed) {
                break;
            }
            refresh();
        }
    }

    private ItemStack vanillaOutput(ServerPlayer player) {
        var level = player.level();
        var grid = ownerMenu.getCraftingMatrix();
        var input = CraftingInput.of(3, 3, IntStream.range(0, grid.size())
                .mapToObj(grid::getStackInSlot)
                .toList());
        var recipe = findRecipe(input, level);
        return recipe == null ? ItemStack.EMPTY : recipe.value().assemble(input);
    }

    /** 普通点击把产物放到光标上，批量合成填满物品栏；提交时费用已扣。
     * @param action 手势，shift 点击按玩家预期填满物品栏
     * @return 产物去了光标还是物品栏；放不下为 {@code false} */
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

    /** 把失败枚举翻成给玩家看的键；未知值也留个能读的回退。 */
    private static String failureKey(ArcaneCraftingTransaction.Failure failure) {
        return switch (failure) {
            case NO_RECIPE -> "gui.thaumicenergistics_ce.arcane_terminal.failure.no_recipe";
            case RESEARCH_LOCKED -> "gui.thaumicenergistics_ce.arcane_terminal.failure.research_locked";
            case INGREDIENTS_CHANGED -> "gui.thaumicenergistics_ce.arcane_terminal.failure.ingredients_changed";
            case PAYMENT_UNAVAILABLE -> "gui.thaumicenergistics_ce.arcane_terminal.failure.payment_unavailable";
            case NOT_SERVER_THREAD, INVALID_CONTEXT, NONE ->
                    "gui.thaumicenergistics_ce.arcane_terminal.failure.internal";
        };
    }

    /** 按当前网格建输入，没有可匹配的返回 {@code null}。 */
    private @Nullable TerminalArcaneCraftingInput buildInput() {
        if (!ownerMenu.canCraft()) {
            return null;
        }
        // 九个单元，空的也算：Thaumaturge 不管网格里有什么都按九个索引。
        // 列表不能裁剪，见 [TerminalArcaneCraftingInput]。
        List<ItemStack> cells = IntStream.range(0, PartArcaneCraftingTerminal.GRID_SIZE)
                .mapToObj(i -> ownerMenu.getCraftingMatrix().getStackInSlot(i))
                .toList();
        ItemStack wand = ownerMenu.wandInventory().getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT);
        List<ItemStack> crystals = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            crystals.add(ownerMenu.crystalInventory().getStackInSlot(i));
        }
        boolean visConnection = ownerMenu.hasVisConnectionCard();
        return new TerminalArcaneCraftingInput(
                cells, serverPlayer, wand, crystals, part, ownerMenu.auraPayer(), visConnection, ownerMenu.crystalFirst).withVisCard(ownerMenu.visCard());
    }

    private ArcaneWorkbenchContext workbenchContext(ServerPlayer server) {
        return ArcaneWorkbenchContext.virtual(
                server, PartArcaneCraftingTerminal.CONTEXT_HOST, server.getUUID());
    }
}
