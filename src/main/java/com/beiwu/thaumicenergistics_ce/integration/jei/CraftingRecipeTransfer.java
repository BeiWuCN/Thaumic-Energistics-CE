package com.beiwu.thaumicenergistics_ce.integration.jei;

import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.menu.me.common.IClientRepo;
import appeng.menu.SlotSemantics;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 让 JEI 用普通合成配方填奥术合成终端的网格。
 * 这是与奥术处理器并列的第二个处理器，少一个 JEI 按钮就会被当成故障。
 * 与奥术处理器不同，它传配方 id，由 AE2 在原版配方管理器里解析。
 * 转移显式写出，不交给 JEI：JEI 只认识玩家的物品栏，
 * 网络里的库存只有 AE2 自己看得见。
 */
public class CraftingRecipeTransfer
        implements IRecipeTransferInfo<MenuArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>>,
                IRecipeTransferHandler<MenuArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>> {

    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 3;

    /** JEI 的合成布局总是 3 列，与配方自己的宽度无关。 */
    private static final int JEI_GRID_WIDTH = 3;

    private final IRecipeTransferHandlerHelper helper;

    public CraftingRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    // IRecipeTransferInfo

    @Override
    public Class<? extends MenuArcaneCraftingTerminal> getContainerClass() {
        return MenuArcaneCraftingTerminal.class;
    }

    /**
     * 这个菜单类的任意菜单类型：有线和无线终端共用它，只写一个会让另一个没有转移按钮。
     */
    @Override
    public Optional<MenuType<MenuArcaneCraftingTerminal>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    /**
     * 这个配方摆不摆得进网格。问的是配方本身，不是扁平原料列表：
     * 拿列表做 3x3 检查会接受一个 4 格宽的配方。
     */
    @Override
    public boolean canHandle(MenuArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        return recipe.value().canCraftInDimensions(GRID_WIDTH, GRID_HEIGHT);
    }
    @Override
    public List<Slot> getRecipeSlots(MenuArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        List<Slot> grid = menu.getSlots(SlotSemantics.CRAFTING_GRID);
        return grid.size() == PartArcaneCraftingTerminal.GRID_SIZE ? grid : List.of();
    }

    @Override
    public List<Slot> getInventorySlots(MenuArcaneCraftingTerminal menu, RecipeHolder<CraftingRecipe> recipe) {
        return menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }

    // IRecipeTransferHandler

    // JEI 19.57 里旧的 6 参数 [transferRecipe] 是接口唯一的抽象方法。
    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            MenuArcaneCraftingTerminal menu,
            RecipeHolder<CraftingRecipe> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        CraftingRecipe recipe = holder.value();
        if (!recipe.canCraftInDimensions(GRID_WIDTH, GRID_HEIGHT) || recipe.getIngredients().isEmpty()) {
            return helper.createInternalError();
        }
        if (getRecipeSlots(menu, holder).size() != PartArcaneCraftingTerminal.GRID_SIZE) {
            return helper.createInternalError();
        }

        // 先查供给再决定转不转。原版配方由 AE2 的数据包按 id 解析并自己取料，
        // 缺料时服务端只填它能填的那几格，玩家看到的是半张网格，
        // 得靠点下去没反应才知道差一味。[ArcaneCraftingRecipeTransfer] 一直是先查后转的，
        // 这里少了这一步，两个 JEI 页签对同一个缺料配方的表现就不一样。
        List<Integer> missingCells = missingCellsFor(recipe, menu.getClientRepo(), player);
        if (!missingCells.isEmpty()) {
            // 整次拒绝，不部分填充：标出缺的那几格，玩家不用读提示就知道差哪一味。
            List<IRecipeSlotView> inputs = display.getSlotViews(RecipeIngredientRole.INPUT);
            List<IRecipeSlotView> highlight = new ArrayList<>(missingCells.size());
            for (int cell : missingCells) {
                if (cell < inputs.size()) {
                    highlight.add(inputs.get(cell));
                }
            }
            Component message = Component.translatable("thaumicenergistics_ce.jei.transfer.missing_ingredients");
            return highlight.isEmpty()
                    ? helper.createUserErrorWithTooltip(message)
                    : helper.createUserErrorForMissingSlots(message, highlight);
        }
        if (!doTransfer) {
            return null;
        }

        // 模板发空列表：数据包按 id 解析配方并读自己的原料，解析不了的已在上面拒掉。
        // 再带一份副本等于回答没人问过的问题。
        NonNullList<ItemStack> templates =
                NonNullList.withSize(PartArcaneCraftingTerminal.GRID_SIZE, ItemStack.EMPTY);
        PacketDistributor.sendToServer(
                new FillCraftingGridFromRecipePacket(holder.id(), templates, false));
        return null;
    }

    // 缺料检查

    /**
     * 哪些格子没有东西能供给。原版配方的 {@code getIngredients()} 对有序配方是
     * {@code width*height} 大小、行优先、空格子为 {@link Ingredient#EMPTY}，
     * 所以跳掉 EMPTY 不会把布局里的留白读成缺料。
     * <p>
     * 返回的是 <b>JEI 输入槽视图</b>的下标，不是配方自己的下标：
     * 配方只有 {@code width} 宽，JEI 总是摆成 3 宽，直接传配方下标会让
     * 2x2 配方第二行的缺料标到右上角。
     */
    private static List<Integer> missingCellsFor(
            CraftingRecipe recipe, @Nullable IClientRepo repo, Player player) {
        List<Ingredient> ingredients = recipe.getIngredients();
        // 无序配方没有布局可言，JEI 按列表顺序铺；有序的按自己宽度换算。
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : JEI_GRID_WIDTH;
        List<Integer> missing = new ArrayList<>();
        for (int cell = 0; cell < ingredients.size(); cell++) {
            Ingredient ingredient = ingredients.get(cell);
            if (ingredient.isEmpty()) {
                continue;
            }
            if (ArcaneCraftingRecipeTransfer
                    .pickSuppliable(List.of(ingredient.getItems()), repo, player)
                    .isEmpty()) {
                missing.add(toJeiIndex(cell, width));
            }
        }
        return missing;
    }

    /** 配方下标（按 {@code width} 行优先）换算成 JEI 的 3 宽下标。 */
    private static int toJeiIndex(int cell, int width) {
        if (width <= 0 || width >= JEI_GRID_WIDTH) {
            return cell;
        }
        return (cell % width) + (cell / width) * JEI_GRID_WIDTH;
    }
}
