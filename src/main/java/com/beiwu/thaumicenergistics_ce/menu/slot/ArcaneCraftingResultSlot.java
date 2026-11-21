package com.beiwu.thaumicenergistics_ce.menu.slot;

import appeng.api.inventories.InternalInventory;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import appeng.helpers.ICraftingGridMenu;
import appeng.helpers.InventoryAction;
import appeng.menu.slot.CraftingTermSlot;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftCost;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneCraftingTransaction;
import com.leclowndu93150.thaumaturge.api.recipe.ArcaneWorkbenchContext;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneCraftingStore;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.workbench.WorkbenchPayment;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
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

    /** 原版网格宽高。终端的合成格固定 3x3，与工作台的任意包围盒不同。 */
    private static final int VANILLA_GRID = 3;

    /**
     * 当前网格命中的原版合成配方，奥术配方配上了就是 {@code null}。
     * 三个字段一起存：点击时要拿同一份输入去扣料，不能重读已经变了的网格。
     */
    private record VanillaMatch(RecipeHolder<CraftingRecipe> holder, CraftingInput input) {}

    private @Nullable VanillaMatch vanillaMatch;

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
            ownerMenu.sendCraftCost(null, 0);
            return;
        }
        TerminalArcaneCraftingInput input = buildInput();
        if (input == null) {
            setDisplayedCraftingOutput(ItemStack.EMPTY);
            ownerMenu.sendCraftCost(null, 0);
            lastFailure = ArcaneCraftingTransaction.Failure.NONE;
            return;
        }
        var result = ArcaneCraftingTransaction.preview(workbenchContext(), serverPlayer, input);
        lastFailure = result.failure();
        if (result.successful()) {
            vanillaMatch = null;
            setDisplayedCraftingOutput(result.output());
            // 和结果一起发，屏幕就不会给已经变了的网格画开销。
            ArcaneCraftCost cost = result.cost();
            ownerMenu.sendCraftCost(cost, cost == null ? 0 : discountPercent(input, cost));
            return;
        }

        ThELog.LOG.info("[arcane] no craft offered for the grid: {}", result.failure());
        // 奥术配方根本没匹配上时回落到原版合成：Thaumaturge 的工作台本身就这么做，
        // 见 MenuArcaneWorkbench#refreshResult。少了这条，JEI 把普通合成配方转移进网格
        // 之后结果槽永远空白，看着像「部分配方识别不了」。
        // 只有 NO_RECIPE 才回落：研究锁着、材料或灵气不足是另一种失败，
        // 那时配方是匹配上的，再试原版等于把玩家的奥术配方换成一个不相干的产物。
        if (result.failure() == ArcaneCraftingTransaction.Failure.NO_RECIPE && tryVanilla()) {
            return;
        }

        vanillaMatch = null;
        setDisplayedCraftingOutput(ItemStack.EMPTY);
        // 失败也照发：灵气、法杖要素或晶体不够时配方其实是匹配上的，
        // 这几行正该留着告诉玩家差多少，而不是整片消失。
        ArcaneCraftCost cost = result.cost();
        ownerMenu.sendCraftCost(cost, cost == null ? 0 : discountPercent(input, cost));
    }

    /**
     * 把当前九格当原版合成台算一次产物，算得出来就缓存配方并返回 {@code true}。
     * 与工作台不同，这里的网格固定 3x3，不需要去猜包围盒。
     */
    private boolean tryVanilla() {
        vanillaMatch = null;
        if (!(serverPlayer.level() instanceof ServerLevel level)) {
            return false;
        }
        CraftingInput craftingInput = craftingInputSnapshot();
        if (craftingInput.isEmpty()) {
            return false;
        }
        var match = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, craftingInput, level);
        if (match.isEmpty()) {
            return false;
        }
        ItemStack output = match.get().value().assemble(craftingInput, level.registryAccess());
        if (output.isEmpty()) {
            return false;
        }
        vanillaMatch = new VanillaMatch(match.get(), craftingInput);
        // 原版配方没有 Vis，开销栏整块清空，屏幕不会显示一行陈旧读数。
        setDisplayedCraftingOutput(output);
        ownerMenu.sendCraftCost(null, 0);
        lastFailure = ArcaneCraftingTransaction.Failure.NONE;
        return true;
    }

    /** 九格按行优先装成 3x3 的原版合成输入。 */
    private CraftingInput craftingInput() {
        List<ItemStack> cells = IntStream.range(0, PartArcaneCraftingTerminal.GRID_SIZE)
                .mapToObj(i -> ownerMenu.craftingInventory().getStackInSlot(i))
                .toList();
        return CraftingInput.of(VANILLA_GRID, VANILLA_GRID, cells);
    }

    /**
     * 给缓存用的快照。{@link CraftingInput#of} 不复制，直接传活网格的引用
     * 会把快照变成「随时会变的现在」：扣料后重比就再也对不上，
     * 而消费表要的恰恰是「点下去那一刻的网格」。
     */
    private CraftingInput craftingInputSnapshot() {
        List<ItemStack> cells = IntStream.range(0, PartArcaneCraftingTerminal.GRID_SIZE)
                .mapToObj(i -> ownerMenu.craftingInventory().getStackInSlot(i).copy())
                .toList();
        return CraftingInput.of(VANILLA_GRID, VANILLA_GRID, cells);
    }

    /**
     * 灵气减免百分比。工作台那一行在「未聚焦」和「n% 减免」之间二选一，
     * 比的正是未聚焦开销与实际开销：法杖杖端和减免装备把前者压到后者。
     * @return 0 表示没有减免，屏幕说「未聚焦」
     */
    private int discountPercent(TerminalArcaneCraftingInput input, ArcaneCraftCost cost) {
        if (cost == null || cost.auraVis() <= 0) {
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
        int crude = WorkbenchPayment.crudeCost(recipe);
        if (crude <= 0) {
            return 0;
        }
        int percent = (int) Math.round((crude - cost.auraVis()) * 100.0 / crude);
        return Math.max(0, Math.min(100, percent));
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
                // 奥术配方没匹配上时走原版合成；两者都配不上才真的拒绝。
                // 顺序与工作台一致（见 MenuArcaneWorkbench#craft），
                // 否则奥术研究未解锁的玩家连原版配方都合不了。
                if (result.failure() == ArcaneCraftingTransaction.Failure.NO_RECIPE) {
                    VanillaCraft vanilla = craftVanilla(server, action, who);
                    if (vanilla == VanillaCraft.CRAFTED) {
                        refresh();
                        continue;
                    }
                    // 成交但物品栏放不下：产物已丢在地上。停手，不把这件事
                    // 报成「合成被拒」——它确实合成了，和奥术路径的 !placed 一致。
                    if (vanilla == VanillaCraft.CRAFTED_NOT_PLACED) {
                        refresh();
                        break;
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

    /** 原版合成这一步的结果。三态而非布尔：成交了但放不下与没成交，调用方处理不同。 */
    private enum VanillaCraft {
        /** 没有可用的原版配方，或网格已经变了。 */
        NOT_CRAFTED,
        /** 扣料并交付了。 */
        CRAFTED,
        /** 扣料了，产物进了物品栏或掉在地上，不能继续下一轮。 */
        CRAFTED_NOT_PLACED
    }

    /**
     * 走一次原版合成：用缓存里的配方与输入，扣料与产物同时落地。
     * <p>
     * 仓库另建一份 3x3 全域的，不复用 {@link #buildInput()} 那个包围盒口径的：
     * 原版配方的 {@link CraftingInput} 永远是整张 3x3，索引对不上就会扣错格。
     */
    private VanillaCraft craftVanilla(ServerPlayer server, InventoryAction action, Player who) {
        // 网格可能在 refresh 之后变过，拿缓存前先重算一次。
        if (vanillaMatch == null && !tryVanilla()) {
            return VanillaCraft.NOT_CRAFTED;
        }
        VanillaMatch match = vanillaMatch;
        if (match == null) {
            return VanillaCraft.NOT_CRAFTED;
        }
        CraftingRecipe recipe = match.holder().value();
        CraftingInput input = match.input();
        // 拿当前网格重建一次再比：缓存的输入是快照，拿它自比恒为 true。
        // 真正能挡住并发变化的仍然是 store.consume 里的逐格比对，这一行是早退。
        if (!recipe.matches(craftingInput(), serverPlayer.level())) {
            return VanillaCraft.NOT_CRAFTED;
        }
        ItemStack output = recipe.assemble(input, serverPlayer.level().registryAccess());
        if (output.isEmpty()) {
            return VanillaCraft.NOT_CRAFTED;
        }
        var store = new TerminalArcaneCraftingStore(
                ownerMenu.craftingInventory(),
                ownerMenu.crystalInventory(),
                ownerMenu.wandInventory(),
                who,
                0,
                0,
                VANILLA_GRID,
                VANILLA_GRID);
        // 原版配方不取晶体，法杖也不变：两栏按「没有」填，仓库便会跳过它们。
        var consumption = new IArcaneCraftingStore.Consumption(
                input.items(),
                recipe.getRemainingItems(input),
                AspectList.EMPTY,
                ownerMenu.wandInventory().getStackInSlot(PartArcaneCraftingTerminal.WAND_SLOT));
        boolean consumed;
        try (var transaction = com.leclowndu93150.thaumaturge.api.transaction.Transaction.openRoot()) {
            consumed = store.consume(consumption, transaction);
            if (consumed) transaction.commit();
        }
        if (!consumed) {
            ThELog.LOG.info("[arcane] vanilla craft aborted: grid changed before consume");
            return VanillaCraft.NOT_CRAFTED;
        }
        boolean placed = deliver(output.copy(), action, who);
        ThELog.LOG.info("[arcane] vanilla craft committed: produced={} placed={}", output, placed);
        return placed ? VanillaCraft.CRAFTED : VanillaCraft.CRAFTED_NOT_PLACED;
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
                cells, serverPlayer, wand, crystals, part, ownerMenu.auraPayer(), visConnection,
                ownerMenu.crystalFirst());
    }

    /** 归这台机器和这名玩家的虚拟工作台：线缆上的终端没有方块可指。 */
    private ArcaneWorkbenchContext workbenchContext() {
        return ArcaneWorkbenchContext.virtual(
                serverPlayer, PartArcaneCraftingTerminal.CONTEXT_HOST, serverPlayer.getUUID());
    }
}
