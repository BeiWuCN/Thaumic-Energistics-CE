package thaumicenergistics_ce.integration.jei;

import com.leclowndu93150.thaumaturge.compat.jei.category.CrucibleCategory;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;

final class CrucibleJeiRecipeType {

    private CrucibleJeiRecipeType() {}

    static IRecipeType<RecipeHolder<?>> crucible() {
        IRecipeType<?> type = CrucibleCategory.RECIPE_TYPE;
        @SuppressWarnings("unchecked")
        IRecipeType<RecipeHolder<?>> cast = (IRecipeType<RecipeHolder<?>>) (IRecipeType<?>) type;
        return cast;
    }
}
