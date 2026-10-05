package thaumicenergistics_ce.integration.jei;

import com.leclowndu93150.thaumaturge.compat.jei.category.ArcaneWorkbenchCategory;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Thaumaturge's arcane workbench recipe type, borrowed not mirrored: a second page would disagree with the
 * one players see. Apart from {@code ArcaneRecipeTypes} because only JEI asks for it, and the JEI classes
 * of another mod are the one thing a dedicated server must never touch.
 * <ul>
 * <li>Resolved inside the method, never a static field, so nothing here is looked at before JEI loads.
 * </ul>
 */
final class ArcaneJeiRecipeType {

    private ArcaneJeiRecipeType() {}

    /** Thaumaturge's own workbench recipe type. */
    static RecipeType<RecipeHolder<?>> arcane() {
        RecipeType<?> type = ArcaneWorkbenchCategory.RECIPE_TYPE;
        @SuppressWarnings("unchecked")
        RecipeType<RecipeHolder<?>> cast = (RecipeType<RecipeHolder<?>>) (RecipeType<?>) type;
        return cast;
    }
}
