package com.beiwu.thaumicenergistics_ce.mixin;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Smithing recipes do not expose their ingredients through Recipe.getIngredients on 1.21.1. */
@Mixin({SmithingTransformRecipe.class, SmithingTrimRecipe.class})
public interface SmithingRecipeIngredientsAccessor {
    @Accessor("template") Ingredient tce$template();
    @Accessor("base") Ingredient tce$base();
    @Accessor("addition") Ingredient tce$addition();
}
