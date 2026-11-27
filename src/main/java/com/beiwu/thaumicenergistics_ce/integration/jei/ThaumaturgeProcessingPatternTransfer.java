package com.beiwu.thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.GenericStack;
import appeng.integration.modules.itemlists.EncodingHelper;
import appeng.menu.me.items.PatternEncodingTermMenu;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.content.infusion.InfusionRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipe;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;

/** Encodes a fixed-output Thaumaturge recipe as an AE2 processing pattern. */
final class ThaumaturgeProcessingPatternTransfer
        implements IRecipeTransferHandler<PatternEncodingTermMenu, RecipeHolder<?>> {

    private final IRecipeTransferHandlerHelper helper;
    private final RecipeType<RecipeHolder<?>> recipeType;

    ThaumaturgeProcessingPatternTransfer(IRecipeTransferHandlerHelper helper, RecipeType<RecipeHolder<?>> recipeType) {
        this.helper = helper;
        this.recipeType = recipeType;
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
    public RecipeType<RecipeHolder<?>> getRecipeType() {
        return recipeType;
    }

    @Override
    @SuppressWarnings("removal")
    public @Nullable IRecipeTransferError transferRecipe(
            PatternEncodingTermMenu menu, RecipeHolder<?> holder, IRecipeSlotsView display,
            Player player, boolean maxTransfer, boolean doTransfer) {
        List<Ingredient> ingredients = new ArrayList<>();
        AspectList aspects;
        GenericStack result;
        if (holder.value() instanceof CrucibleRecipe recipe) {
            ingredients.add(recipe.catalyst());
            aspects = recipe.aspects();
            result = GenericStack.fromItemStack(recipe.rawResult());
        } else if (holder.value() instanceof InfusionRecipe recipe) {
            ingredients.add(recipe.catalyst());
            ingredients.addAll(recipe.components());
            aspects = recipe.aspects();
            result = GenericStack.fromItemStack(recipe.getResultItem(player.registryAccess()));
        } else {
            return helper.createInternalError();
        }

        List<List<GenericStack>> inputs = new ArrayList<>();
        for (Ingredient ingredient : ingredients) {
            var variants = Arrays.stream(ingredient.getItems())
                    .map(GenericStack::fromItemStack)
                    .filter(Objects::nonNull)
                    .toList();
            if (variants.isEmpty()) {
                return helper.createInternalError();
            }
            inputs.add(variants);
        }
        for (var entry : aspects.entries()) {
            inputs.add(List.of(new GenericStack(AEssentiaKey.of(entry.aspect()), entry.amount())));
        }
        if (result == null) {
            return helper.createInternalError();
        }
        if (inputs.size() > menu.getProcessingInputSlots().length
                || menu.getProcessingOutputSlots().length == 0) {
            return helper.createUserErrorWithTooltip(Component.translatable(
                    "thaumicenergistics_ce.jei.transfer.pattern_too_large"));
        }
        if (doTransfer) {
            EncodingHelper.encodeProcessingRecipe(menu, inputs, List.of(result));
        }
        return null;
    }
}
