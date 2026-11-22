package com.beiwu.thaumicenergistics_ce.mixin;

import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.compat.jei.category.ArcaneWorkbenchCategory;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneCraftingRecipe;
import com.beiwu.thaumicenergistics_ce.recipe.arcane.EssentiaCrystals;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcWorkbench;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.IFocusGroup;

import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ArcaneWorkbenchCategory.class, remap = false)
abstract class ArcaneJeiCrystalLayoutMixin {
    @Shadow @Final private static int CRYSTAL_X;
    @Shadow @Final private static int CRYSTAL_Y;
    @Shadow @Final private static int CRYSTAL_SPACING;

    @Shadow
    private void layoutCrystals(IRecipeLayoutBuilder builder, AspectList crystals) {
        throw new AssertionError();
    }

    @Redirect(
            method = "setRecipe(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lnet/minecraft/world/item/crafting/RecipeHolder;Lmezz/jei/api/recipe/IFocusGroup;)V",
            at = @At(value = "INVOKE", target = "Lcom/leclowndu93150/thaumaturge/compat/jei/category/ArcaneWorkbenchCategory;layoutCrystals(Lmezz/jei/api/gui/builder/IRecipeLayoutBuilder;Lcom/leclowndu93150/thaumaturge/api/aspect/AspectList;)V"),
            require = 0)
    private void tece$compactCrystalColumn(ArcaneWorkbenchCategory category, IRecipeLayoutBuilder builder,
            AspectList crystals, IRecipeLayoutBuilder recipeBuilder, RecipeHolder<ArcaneCraftingRecipe> holder,
            IFocusGroup focuses) {
        if (!ThEIds.MODID.equals(holder.id().identifier().getNamespace())) {
            layoutCrystals(builder, crystals);
            return;
        }


        int row = 0;
        for (int index = 0; index < 6; index++) {
            var primal = TcWorkbench.primalAt(index);
            for (var entry : crystals.entries()) {
                if (entry.amount() <= 0 || !entry.aspect().is(primal)) {
                    continue;
                }
                var stack = EssentiaCrystals.create(entry.aspect(), entry.amount());
                builder.addInputSlot(CRYSTAL_X, CRYSTAL_Y + row * CRYSTAL_SPACING)
                        .add(stack);
                row++;
                break;
            }
        }

        // Retain the category's six input slots for recipe transfer handlers.
        for (; row < 6; row++) {
            builder.addInputSlot(CRYSTAL_X, CRYSTAL_Y + row * CRYSTAL_SPACING);
        }
    }
}
