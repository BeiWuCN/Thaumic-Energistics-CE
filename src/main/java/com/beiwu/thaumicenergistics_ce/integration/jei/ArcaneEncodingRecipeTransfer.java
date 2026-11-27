package com.beiwu.thaumicenergistics_ce.integration.jei;

import java.util.List;
import java.util.Optional;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import appeng.menu.SlotSemantics;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneEncodingTerminal;
import com.beiwu.thaumicenergistics_ce.network.ArcaneEncodingFillPayload;

/**
 * 把奥术工作台配方转进 ME 奥术编码终端的九格编码网格。
 * 只接奥术配方分类：原版工作台配方不在这里出现，编码终端也只认奥术合成。
 *
 * <p>照 AE2 样板编码终端的规矩：编码网格记的是"要编什么"，不是"现在有什么"。
 * 所以这里不查网络库存、也不查背包，每格直接取配方的第一个成员；
 * 材料够不够是组装机执行时的事，和编码无关。
 */
public class ArcaneEncodingRecipeTransfer
        implements IRecipeTransferInfo<MenuArcaneEncodingTerminal, RecipeHolder<?>>,
                IRecipeTransferHandler<MenuArcaneEncodingTerminal, RecipeHolder<?>> {

    private final IRecipeTransferHandlerHelper helper;

    public ArcaneEncodingRecipeTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<? extends MenuArcaneEncodingTerminal> getContainerClass() {
        return MenuArcaneEncodingTerminal.class;
    }

    @Override
    public Optional<MenuType<MenuArcaneEncodingTerminal>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<RecipeHolder<?>> getRecipeType() {
        return ArcaneJeiRecipeType.arcane();
    }

    @Override
    public boolean canHandle(MenuArcaneEncodingTerminal menu, RecipeHolder<?> recipe) {
        return ArcaneRecipeTypes.fitsGrid(recipe);
    }

    @Override
    public List<Slot> getRecipeSlots(MenuArcaneEncodingTerminal menu, RecipeHolder<?> recipe) {
        List<Slot> grid = menu.getSlots(SlotSemantics.CRAFTING_GRID);
        return grid.size() == MenuArcaneEncodingTerminal.GRID_SIZE ? grid : List.of();
    }

    @Override
    public List<Slot> getInventorySlots(MenuArcaneEncodingTerminal menu, RecipeHolder<?> recipe) {
        return menu.getSlots(SlotSemantics.PLAYER_INVENTORY);
    }

    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            MenuArcaneEncodingTerminal menu,
            RecipeHolder<?> holder,
            IRecipeSlotsView display,
            Player player,
            boolean maxTransfer,
            boolean doTransfer) {
        List<List<ItemStack>> cells = ArcaneRecipeTypes.cellsFor(holder);
        if (cells == null) {
            return helper.createInternalError();
        }
        if (getRecipeSlots(menu, holder).size() != MenuArcaneEncodingTerminal.GRID_SIZE) {
            return helper.createInternalError();
        }

        NonNullList<ItemStack> templates =
                NonNullList.withSize(MenuArcaneEncodingTerminal.GRID_SIZE, ItemStack.EMPTY);
        for (int cell = 0; cell < MenuArcaneEncodingTerminal.GRID_SIZE && cell < cells.size(); cell++) {
            for (ItemStack variant : cells.get(cell)) {
                if (!variant.isEmpty()) {
                    templates.set(cell, variant.copyWithCount(1));
                    break;
                }
            }
        }

        if (!doTransfer) {
            return null;
        }
        PacketDistributor.sendToServer(new ArcaneEncodingFillPayload(menu.containerId, List.copyOf(templates)));
        return null;
    }
}
