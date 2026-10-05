package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * Whether a 3x3 grid stands for an arcane recipe: shaped recipes are placed and mirrored, shapeless
 * ones are matched as a full assignment. Also checks what a pattern accepts, cell by cell.
 */
final class ArcaneGridMatcher {

    private ArcaneGridMatcher() {}

    static boolean satisfiesGrid(IArcaneRecipe recipe, List<ItemStack> cells, @Nullable Level level) {
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            List<Ingredient> ingredients = shaped.getIngredients();
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            if (width < 1 || height < 1 || width > ThEArcanePattern.GRID_SIDE
                    || height > ThEArcanePattern.GRID_SIDE) {
                return false;
            }
            if (ingredients.size() < width * height) {
                return false;
            }
            for (int originX = 0; originX + width <= ThEArcanePattern.GRID_SIDE; originX++) {
                for (int originY = 0; originY + height <= ThEArcanePattern.GRID_SIDE; originY++) {
                    for (boolean mirrored : new boolean[] {false, true}) {
                        if (fitsAt(cells, ingredients, width, height, originX, originY, mirrored)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        if (recipe instanceof ArcaneShapelessCraftingRecipe shapeless) {
            return fitsShapeless(cells, shapeless.getIngredients());
        }
        return false;
    }

    /**
     * Whether every non-empty {@code inputs} entry is consumed as a grid cell or a crystal: multiplicity
     * is not checked, the live recipe is re-matched before the craft.
     */
    static boolean acceptsInputs(
            List<ItemStack> grid, List<TagKey<Item>> cellTags, AspectList crystals, List<ItemStack> inputs) {
        int matched = 0;
        for (ItemStack input : inputs) {
            if (input.isEmpty()) {
                continue;
            }
            if (!matchesGridCell(grid, cellTags, input) && !matchesCrystalItem(crystals, input)) {
                return false;
            }
            matched++;
        }
        return matched > 0;
    }

    private static boolean matchesGridCell(
            List<ItemStack> grid, List<TagKey<Item>> cellTags, ItemStack input) {
        for (int cell = 0; cell < grid.size(); cell++) {
            ItemStack stored = grid.get(cell);
            if (stored.isEmpty()) {
                continue;
            }
            TagKey<Item> tag = tagAt(cellTags, cell);
            if (tag != null) {
                if (input.is(tag)) {
                    return true;
                }
                continue;
            }
            if (ItemStack.isSameItemSameComponents(stored, input)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesCrystalItem(AspectList crystals, ItemStack input) {
        for (AspectInstance crystal : crystals.entries()) {
            ItemStack wanted = TcRegistry.crystalFor(crystal.aspect(), crystal.amount());
            if (!wanted.isEmpty() && ItemStack.isSameItemSameComponents(wanted, input)) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable TagKey<Item> tagAt(List<TagKey<Item>> cellTags, int cell) {
        if (cell < 0 || cell >= cellTags.size()) {
            return null;
        }
        return cellTags.get(cell);
    }

    /**
     * One placement of a shaped recipe: ingredients are indexed by the recipe's row width, not the
     * grid's, or a two-wide pattern read three cells at a time compares the wrong columns.
     */
    private static boolean fitsAt(
            List<ItemStack> cells,
            List<Ingredient> ingredients,
            int width,
            int height,
            int originX,
            int originY,
            boolean mirrored) {
        for (int y = 0; y < ThEArcanePattern.GRID_SIDE; y++) {
            for (int x = 0; x < ThEArcanePattern.GRID_SIDE; x++) {
                ItemStack cell = cells.get(y * ThEArcanePattern.GRID_SIDE + x);
                int localX = x - originX;
                int localY = y - originY;
                boolean covered = localX >= 0 && localX < width && localY >= 0 && localY < height;
                if (!covered) {
                    if (!cell.isEmpty()) {
                        return false;
                    }
                    continue;
                }
                int column = mirrored ? width - localX - 1 : localX;
                Ingredient ingredient = ingredients.get(localY * width + column);
                if (ingredient == null || !ingredient.test(cell)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a shapeless recipe's ingredients are present with nothing left over: a full matching, like
     * the workbench's {@code RecipeMatcher}, not a greedy scan (oak must take the "oak planks" slot).
     */
    private static boolean fitsShapeless(List<ItemStack> cells, List<Ingredient> ingredients) {
        List<ItemStack> present = new ArrayList<>(cells.size());
        for (ItemStack cell : cells) {
            if (!cell.isEmpty()) {
                present.add(cell);
            }
        }
        if (present.size() != ingredients.size()) {
            return false;
        }
        return assignable(present, ingredients, new boolean[ingredients.size()], 0);
    }

    private static boolean assignable(
            List<ItemStack> items, List<Ingredient> ingredients, boolean[] used, int index) {
        if (index >= items.size()) {
            return true;
        }
        ItemStack item = items.get(index);
        for (int i = 0; i < ingredients.size(); i++) {
            if (used[i] || !ingredients.get(i).test(item)) {
                continue;
            }
            used[i] = true;
            if (assignable(items, ingredients, used, index + 1)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }
}
