package com.beiwu.thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.beiwu.thaumicenergistics_ce.client.jei.AspectFromItemCategory;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jspecify.annotations.Nullable;

/**
 * 把「从物品提取要素」转成 ME 样板编码终端的处理样板：
 * 物品是输入，要素是输出。
 *
 * <p>和坩埚/注魔那条 {@link ThaumaturgeProcessingPatternTransfer} 正好反向——
 * 那边要素是耗材（输入）、物品是产物，这边要素才是蒸馏的产物。
 */
final class AspectFromItemTransfer
        implements IRecipeTransferHandler<PatternEncodingTermMenu, AspectFromItemRecipe> {

    private final IRecipeTransferHandlerHelper helper;

    AspectFromItemTransfer(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
    }

    @Override
    public Class<PatternEncodingTermMenu> getContainerClass() {
        return PatternEncodingTermMenu.class;
    }

    @Override
    public Optional<MenuType<PatternEncodingTermMenu>> getMenuType() {
        return Optional.empty();
    }

    @Override
    public RecipeType<AspectFromItemRecipe> getRecipeType() {
        return AspectFromItemCategory.RECIPE_TYPE;
    }

    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            PatternEncodingTermMenu menu, AspectFromItemRecipe recipe, IRecipeSlotsView display,
            Player player, boolean maxTransfer, boolean doTransfer) {
        GenericStack source = GenericStack.fromItemStack(recipe.source());
        if (source == null || recipe.aspects().isEmpty()) {
            return helper.createInternalError();
        }

        List<List<GenericStack>> inputs = List.of(List.of(source));
        List<GenericStack> outputs = new ArrayList<>(recipe.aspects().size());
        for (var aspect : recipe.aspects()) {
            outputs.add(new GenericStack(AEssentiaKey.of(aspect.aspect()), Math.max(1L, aspect.amount())));
        }

        if (inputs.size() > menu.getProcessingInputSlots().length
                || outputs.size() > menu.getProcessingOutputSlots().length) {
            return helper.createUserErrorWithTooltip(Component.translatable(
                    "thaumicenergistics_ce.jei.transfer.pattern_too_large"));
        }
        if (doTransfer) {
            EncodingHelper.encodeProcessingRecipe(menu, inputs, outputs);
        }
        return null;
    }
}
