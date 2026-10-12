package thaumicenergistics_ce.recipe.arcane;

import net.minecraft.core.Holder;
import java.util.Optional;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.Nullable;

final class ArcaneRecipeIndex {

    private static final Map<Item, Set<ResourceKey<Recipe<?>>>> ITEM_RECIPES = new HashMap<>();

    private static RecipeManager indexedManager;

    private ArcaneRecipeIndex() {}

    static void index(RecipeManager manager) {
        if (manager == indexedManager) {
            return;
        }
        ITEM_RECIPES.clear();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            for (Item item : acceptedItems(arcane)) {
                ITEM_RECIPES.computeIfAbsent(item, key -> new HashSet<>()).add(holder.id());
            }
        }
        indexedManager = manager;
    }

    static @Nullable Set<ResourceKey<Recipe<?>>> accepting(Item item) {
        return ITEM_RECIPES.get(item);
    }

    private static Set<Item> acceptedItems(IArcaneRecipe recipe) {
        Set<Item> items = new HashSet<>();
        List<Optional<Ingredient>> ingredients;
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            ingredients = shaped.getIngredients();
        } else if (recipe instanceof ArcaneShapelessCraftingRecipe shapeless) {
            ingredients = shapeless.ingredients().stream().map(Optional::of).toList();
        } else {
            return items;
        }
        for (Optional<Ingredient> entry : ingredients) {
            if (entry.isEmpty() || entry.get().isEmpty()) {
                continue;
            }
            entry.get().items().map(Holder::value).forEach(items::add);
        }
        return items;
    }
}
