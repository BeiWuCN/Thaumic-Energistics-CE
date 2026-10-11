package thaumicenergistics_ce.integration.jei;

import com.leclowndu93150.thaumaturge.compat.jei.category.ArcaneWorkbenchCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;

final class ArcaneJeiRecipeType {

    private ArcaneJeiRecipeType() {}

    static IRecipeType<RecipeHolder<?>> arcane() {
        IRecipeType<?> type = ArcaneWorkbenchCategory.RECIPE_TYPE;
        @SuppressWarnings("unchecked")
        IRecipeType<RecipeHolder<?>> cast = (IRecipeType<RecipeHolder<?>>) (IRecipeType<?>) type;
        return cast;
    }
}
