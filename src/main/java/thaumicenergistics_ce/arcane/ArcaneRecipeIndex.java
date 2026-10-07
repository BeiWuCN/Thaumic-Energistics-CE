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
 * 记录哪些奥术配方接受哪些物品，使手工填好的网格在匹配前先缩小候选范围。
 * 配方管理器一旦变化就重建，因为每次重载都会给出一个新的管理器。
 */
final class ArcaneRecipeIndex {

    private static final Map<Item, Set<ResourceLocation>> ITEM_RECIPES = new HashMap<>();

    private static RecipeManager indexedManager;

    private ArcaneRecipeIndex() {}

    /**
     * 把物品登记到所有接受它的材料下：多登记只多一次检测，少登记则会
     * 悄无声息地丢掉配方。
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
