package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.Nullable;

/**
 * 记哪些奥术配方收哪些物品，手工填的网格在匹配前先缩候选。配方管理器一换就重建：
 * 每次重载给出的是新实例。
 */
final class ArcaneRecipeIndex {

    private static final Map<Item, Set<ResourceLocation>> ITEM_RECIPES = new HashMap<>();

    private static RecipeManager indexedManager;

    private ArcaneRecipeIndex() {}

    /**
     * 把物品登记到所有收它的材料下：多登记只多一次检测，少登记会无声丢配方。
     */
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

    static @Nullable Set<ResourceLocation> accepting(Item item) {
        return ITEM_RECIPES.get(item);
    }

    private static Set<Item> acceptedItems(IArcaneRecipe recipe) {
        Set<Item> items = new HashSet<>();
        List<Ingredient> ingredients;
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            ingredients = shaped.getIngredients();
        } else if (recipe instanceof ArcaneShapelessCraftingRecipe shapeless) {
            ingredients = shapeless.getIngredients();
        } else {
            return items;
        }
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) {
                    items.add(stack.getItem());
                }
            }
        }
        return items;
    }
}
