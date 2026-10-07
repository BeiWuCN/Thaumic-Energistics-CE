package thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.AEItemKey;
import appeng.core.network.serverbound.FillCraftingGridFromRecipePacket;
import appeng.menu.SlotSemantics;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.menu.me.common.IClientRepo;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * 用奥术工作台配方填奥术合成终端的合成网格。
 * 用 Thaumaturge 自己的分类，玩家才会在惯常位置找到按钮；槽位来自 SlotSemantics。
 * 不传配方 id：奥术配方不在原版配方管理器里。
 * 每个格子的模板取背后有库存的那个变体，不取配方首先列出的那个，
 * 数据包自己解析模板，不会知道原料原本是个标签。
 * 一个处理器服务两个终端，JEI 只按容器类与配方类型索引。
 */
public class ArcaneCraftingRecipeTransfer
        implements IRecipeTransferInfo<MenuArcaneCraftingTerminal, RecipeHolder<?>>,
                IRecipeTransferHandler<MenuArcaneCraftingTerminal, RecipeHolder<?>> {

    private final IRecipeTransferHandlerHelper helper;

    public ArcaneCraftingRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    // ---- IRecipeTransferInfo -------------------------------------------

    @Override
    public Class<? extends MenuArcaneCraftingTerminal> getContainerClass() {
        return MenuArcaneCraftingTerminal.class;
    }

    /**
     * 这个菜单类的任意菜单类型：有线与无线终端共用它，
     * 只写一个菜单类型会让另一个没转移按钮。
     */
    @Override
    public Optional<MenuType<MenuArcaneCraftingTerminal>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<RecipeHolder<?>> getRecipeType() {
        return ArcaneJeiRecipeType.arcane();
    }

    @Override
    public boolean canHandle(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        return ArcaneRecipeTypes.fitsGrid(recipe);
    }

    @Override
    public List<Slot> getRecipeSlots(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        List<Slot> grid = menu.getSlots(SlotSemantics.CRAFTING_GRID);
        return grid.size() == PartArcaneCraftingTerminal.GRID_SIZE ? grid : List.of();
    }

    @Override
    public List<Slot> getInventorySlots(MenuArcaneCraftingTerminal menu, RecipeHolder<?> recipe) {
        return menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }

    // ---- IRecipeTransferHandler ----------------------------------------

    // JEI 19.57 里这个 6 参数的 [transferRecipe] 是接口唯一的抽象方法。
    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            MenuArcaneCraftingTerminal menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        List<List<ItemStack>> cells = ArcaneRecipeTypes.cellsFor(holder);
        if (cells == null) {
            return helper.createInternalError();
        }
        if (getRecipeSlots(menu, holder).size() != PartArcaneCraftingTerminal.GRID_SIZE) {
            return helper.createInternalError();
        }

        // 每格一个模板，选玩家或网络供得上的变体：
        // 数据包的模板路径不认识标签，标签的第一个成员常常正是没库存的那个。
        IClientRepo repo = menu.getClientRepo();
        NonNullList<ItemStack> templates = NonNullList.withSize(PartArcaneCraftingTerminal.GRID_SIZE, ItemStack.EMPTY);
        boolean missing = false;
        for (int cell = 0; cell < PartArcaneCraftingTerminal.GRID_SIZE; cell++) {
            List<ItemStack> variants = cell < cells.size() ? cells.get(cell) : List.of();
            templates.set(cell, pickSuppliable(variants, repo, player));
            if (templates.get(cell).isEmpty() && asksForSomething(variants)) {
                missing = true;
            }
        }

        // 摆不下就整次拒绝，不部分填充：半填的网格看着像是这个终端合成不了，
        // 不像是在说缺料。
        if (missing) {
            return helper.createUserErrorWithTooltip(
                    Component.translatable("thaumicenergistics_ce.jei.transfer.missing_ingredients"));
        }
        if (!doTransfer) {
            return null;
        }

        // 不传配方 id：配方不在原版管理器里，只能走数据包的模板路径。
        PacketDistributor.sendToServer(new FillCraftingGridFromRecipePacket(null, templates, false));
        return null;
    }

    // ---- templates -----------------------------------------------------

    /**
     * 一个格子的模板：背后供给最多的变体，标签第一个成员没库存也能转移。
     * 没东西能供给这个格子时为空。
     */
    private static ItemStack pickSuppliable(List<ItemStack> variants, @Nullable IClientRepo repo, Player player) {
        ItemStack best = ItemStack.EMPTY;
        long bestSupply = 0;
        for (ItemStack variant : variants) {
            if (variant.isEmpty()) {
                continue;
            }
            long supply = supplyOf(variant, repo, player);
            if (supply > bestSupply) {
                best = variant.copyWithCount(1);
                bestSupply = supply;
            }
        }
        return best;
    }

    /** 网络对这个确切物品报的数量，加玩家身上带的。 */
    private static long supplyOf(ItemStack variant, @Nullable IClientRepo repo, Player player) {
        long supply = 0;
        AEItemKey wanted = AEItemKey.of(variant);
        if (repo != null && wanted != null) {
            for (GridInventoryEntry entry : repo.getByIngredient(Ingredient.of(variant))) {
                if (wanted.equals(entry.getWhat())) {
                    supply += entry.getStoredAmount();
                }
            }
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack carried = player.getInventory().getItem(slot);
            if (ItemStack.isSameItemSameComponents(carried, variant)) {
                supply += carried.getCount();
            }
        }
        return supply;
    }

    /** 这个格子要不要东西：布局里的空格子不能读成缺料。 */
    private static boolean asksForSomething(List<ItemStack> variants) {
        for (ItemStack variant : variants) {
            if (!variant.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
