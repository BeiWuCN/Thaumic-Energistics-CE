package com.beiwu.thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.workbench.WorkbenchPayment;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.TerminalArcaneCraftingInput;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.TerminalArcaneCraftingStore;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.util.ThELog;

/**
 * 奥术合成终端的产物槽位。合成入口点 {@code doClick} 声明在 {@code CraftingTermSlot}。
 * {@code ArcaneCraftingTransaction} 匹配配方并扣费；{@link #refresh} 只预览不付费。
 * 支付来自终端自己的网格、水晶和法杖槽，从不向网络要材料，
 * 见 {@link TerminalArcaneCraftingStore}。
 */
public class ArcaneCraftingResultSlot extends CraftingTermSlot {

    private final @Nullable ServerPlayer serverPlayer;
    private final @Nullable PartArcaneCraftingTerminal part;

    /** 用具体菜单类型，不写 {@link ICraftingGridMenu}：把 vis 开销发到屏幕靠它。 */
    private final com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal ownerMenu;

    /** 上次 {@link #refresh()} 没出产物的原因；没失败就是 {@code NONE}。 */
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
        // 每次取走都走 [doClick] 扣费；原版不能先把产物发出去。
        return false;
    }

    /** 变化时才重算，不每帧：一次完整配方匹配加一次开销计算。 */
    public void refresh() {
        if (serverPlayer == null) {
            return;
        }
        if (!ownerMenu.canCraft()) {
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
        var result = ArcaneCraftingTransaction.preview(workbenchContext(), serverPlayer, input);
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
        if (!result.successful()) {
            ThELog.LOG.info("[arcane] no craft offered for the grid: {}", result.failure());
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
        var inspection = ArcaneCraftingTransaction.inspect(workbenchContext(), serverPlayer, input);
        if (!inspection.successful() || inspection.recipeId() == null) {
            return 0;
        }
        var holder = serverPlayer.level().getRecipeManager().byKey(inspection.recipeId().location()).orElse(null);
        if (holder == null || !(holder.value() instanceof IArcaneRecipe recipe)) {
            return 0;
        }
        int undiscounted = WorkbenchPayment.crudeCost(recipe);
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
        var cost = ArcaneCraftingTransaction.preview(workbenchContext(), serverPlayer, buildInput()).cost();
        return cost == null ? AspectList.EMPTY : cost.crystalsNeeded();
    }

    private ArcaneCraftingTransaction.@Nullable Inspection inspectTransferredRecipe(String recipeId) {
        var input = buildInput();
        if (serverPlayer == null || input == null) {
            return null;
        }
        var context = workbenchContext();
        var inspection = ArcaneCraftingTransaction.inspect(context, serverPlayer, input);
        if (!inspection.successful() || inspection.recipeId() == null
                || !inspection.recipeId().location().toString().equals(recipeId)) {
            return null;
        }
        return inspection;
    }

    public ArcaneCraftingTransaction.Failure lastFailure() {
        return lastFailure;
    }

    @Override
    public void doClick(InventoryAction action, Player who) {
        PartArcaneCraftingTerminal terminal = part;
        if (!ownerMenu.canCraft() || !(who instanceof ServerPlayer server)) {
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
            // 支付来自终端自己的容器：网格、水晶槽、法杖，和 Thaumaturge 工作台一致。
            // 向网络收费等于再要一份玩家已经摆好的东西。
            // 消耗表按输入的包围盒打包，仓库要拿同一套偏移才映射得回九个格位。
            var store = new TerminalArcaneCraftingStore(
                    ownerMenu.craftingInventory(),
                    ownerMenu.crystalInventory(),
                    ownerMenu.wandInventory(),
                    who,
                    input.left(),
                    input.top(),
                    input.width(),
                    input.height());
            ArcaneCraftingTransaction.Result result;
            try (var transaction = com.leclowndu93150.thaumaturge.api.transaction.Transaction.openRoot()) {
                result = ArcaneCraftingTransaction.craft(workbenchContext(), server, input, store, transaction);
                if (result.successful()) transaction.commit();
            }
            if (!result.successful()) {
                if (i == 0 && result.failure() == ArcaneCraftingTransaction.Failure.NO_RECIPE) {
                    ItemStack output = vanillaOutput(server);
                    if (!output.isEmpty()) {
                        setDisplayedCraftingOutput(output);
                        super.doClick(action, who);
                        refresh();
                        return;
                    }
                }
                ThELog.LOG.info(
                        "[arcane] craft click refused: successful={} failure={}",
                        result.successful(), result.failure());
                // 失败原本只写日志，玩家看到的是「点了没反应」。把原因也说出来，
                // 这样配方没匹配、研究没解锁、材料或灵气不够就能当场分清。
                who.displayClientMessage(
                        Component.translatable(
                                "gui.thaumicenergistics_ce.arcane_terminal.craft_failed",
                                Component.translatable(failureKey(result.failure()))),
                        true);
                break;
            }

            // 网格、水晶和法杖已经由 store 扣过；剩余物跟着它回到各自槽位。
            ItemStack output = result.output().copy();
            // 先读再交付：[Inventory#add] 会把数量改成没放进去的那部分。
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

    private ItemStack vanillaOutput(ServerPlayer player) {
        var level = player.level();
        var grid = ownerMenu.craftingInventory();
        var input = CraftingInput.of(3, 3, IntStream.range(0, grid.size())
                .mapToObj(grid::getStackInSlot)
                .toList());
        var recipe = findRecipe(input, level);
        return recipe == null ? ItemStack.EMPTY : recipe.value().assemble(input, level.registryAccess());
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
        // 费用已扣、产物已存在；调用方把这里读成「停」。
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
                .mapToObj(i -> ownerMenu.craftingInventory().getStackInSlot(i))
                .toList();
        ItemStack wand = ownerMenu.wandInventory().getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT);
        // 水晶只从自己的槽位取，不从网格取。
        // 网格单元里的水晶是匹配材料，再算一次支付就对不上了。
        List<ItemStack> crystals = new ArrayList<>(PartArcaneCraftingTerminal.CRYSTAL_SLOTS);
        for (int i = 0; i < PartArcaneCraftingTerminal.CRYSTAL_SLOTS; i++) {
            crystals.add(ownerMenu.crystalInventory().getStackInSlot(i));
        }
        // 卡片只在这里读一次：一次合成的两遍灵气处理都读这个冻结结果，
        // 不再问槽位，提交才不会和模拟对不上。
        boolean visConnection = ownerMenu.hasVisConnectionCard();
        return new TerminalArcaneCraftingInput(
                cells, serverPlayer, wand, crystals, part, ownerMenu.auraPayer(), visConnection, ownerMenu.crystalFirst);
    }

    /** 归这台机器和这名玩家的虚拟工作台：线缆上的终端没有方块可指。 */
    private ArcaneWorkbenchContext workbenchContext() {
        return ArcaneWorkbenchContext.virtual(
                serverPlayer, PartArcaneCraftingTerminal.CONTEXT_HOST, serverPlayer.getUUID());
    }
}
