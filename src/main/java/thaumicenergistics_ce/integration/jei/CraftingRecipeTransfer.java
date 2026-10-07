package thaumicenergistics_ce.integration.jei;

import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.menu.SlotSemantics;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IRecipeTransferInfo;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 让 JEI 用普通合成配方填充奥术合成终端的网格。
 * 与奥术处理器并列的第二个处理器，没有它，缺少的 JEI 按钮就会被当成故障。
 * 与奥术处理器不同，它传配方 id，由 AE2 在原版配方管理器里解析。
 * 转移是显式写出的，而不是交给 JEI，因为 JEI 只认识玩家的物品栏，不认识网络。
 * 网络里的库存只有 AE2 自己看得见。
 */
public class CraftingRecipeTransfer
        implements IRecipeTransferInfo<MenuArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>>,
                IRecipeTransferHandler<MenuArcaneCraftingTerminal, RecipeHolder<CraftingRecipe>> {

    private static final int GRID_WIDTH = 3;
    private static final int GRID_HEIGHT = 3;

    private final IRecipeTransferHandlerHelper helper;

    public CraftingRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    // ---- IRecipeTransferInfo -------------------------------------------

    @Override
    public Class<? extends MenuArcaneCraftingTerminal> getContainerClass() {
        return MenuArcaneCraftingTerminal.class;
    }

    /**
     * 这个菜单类的任意菜单类型：有线与无线终端共用它，只写一个菜单类型会让另一个没有转移按钮。
     * 只写一个菜单类型会让另一个没有转移按钮。
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
     * 这个配方能否摆进网格。问的是配方本身，而不是扁平原料列表：对列表做 3x3 检查
     * 会接受一个 4 格宽的配方。
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

    // ---- IRecipeTransferHandler ----------------------------------------

    // JEI 19.57 里旧的 6 参数 [transferRecipe] 是接口唯一的抽象方法
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
        if (!doTransfer) {
            return null;
        }

        // 模板以空列表发出：数据包按 id 解析配方并读取自己的原料，无法解析的配方已在上面被拒。
        // 再带一份副本等于回答一个没人问过的问题。
        NonNullList<ItemStack> templates =
                NonNullList.withSize(PartArcaneCraftingTerminal.GRID_SIZE, ItemStack.EMPTY);
        PacketDistributor.sendToServer(
                new FillCraftingGridFromRecipePacket(holder.id(), templates, false));
        return null;
    }
}
