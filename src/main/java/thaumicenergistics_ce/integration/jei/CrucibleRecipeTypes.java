package thaumicenergistics_ce.integration.jei;

import appeng.api.stacks.GenericStack;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.content.recipe.crucible.CrucibleRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.recipe.IngredientStacks;

public final class CrucibleRecipeTypes {

    private CrucibleRecipeTypes() {}

    public static boolean isCrucible(RecipeHolder<?> holder) {
        return holder.value() instanceof CrucibleRecipe;
    }

    public static @Nullable List<List<GenericStack>> inputsFor(
            RecipeHolder<?> holder, HolderLookup.Provider registries) {
        if (!(holder.value() instanceof CrucibleRecipe recipe)) {
            return null;
        }
        List<GenericStack> catalyst = new ArrayList<>();
        IngredientStacks.variants(recipe.catalyst(), registries).forEach(item -> {
            GenericStack stack = GenericStack.fromItemStack(item);
            if (stack != null) {
                catalyst.add(stack);
            }
        });
        if (catalyst.isEmpty()) {
            return null;
        }
        List<List<GenericStack>> inputs = new ArrayList<>();
        inputs.add(catalyst);
        for (AspectInstance entry : recipe.aspects().entries()) {
            inputs.add(List.of(new GenericStack(AEssentiaKey.of(entry.aspect()), entry.amount())));
        }
        return inputs;
    }

    public static @Nullable List<GenericStack> outputsFor(RecipeHolder<?> holder) {
        if (!(holder.value() instanceof CrucibleRecipe recipe)) {
            return null;
        }
        GenericStack result = GenericStack.fromItemStack(recipe.rawResult().create());
        return result == null ? null : List.of(result);
    }
}
