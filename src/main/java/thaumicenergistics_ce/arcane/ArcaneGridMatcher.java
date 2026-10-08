package thaumicenergistics_ce.arcane;

import java.util.Optional;
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
 * 3x3 网格是否代表某个奥术配方：有序配方做放置与镜像匹配，无序配方按完整指派匹配。
 * 同时逐格检查样板接受哪些物品。
 */
final class ArcaneGridMatcher {

    private ArcaneGridMatcher() {}

    static boolean satisfiesGrid(IArcaneRecipe recipe, List<ItemStack> cells, @Nullable Level level) {
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            List<Optional<Ingredient>> ingredients = shaped.getIngredients();
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
            return fitsShapeless(cells, shapeless.ingredients());
        }
        return false;
    }

    /**
     * {@code inputs} 中每个非空条目是否都能作为网格格位或晶体被消耗。
     * 不检查数量，合成前会用实时配方重新匹配。
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
     * 有序配方的一次放置匹配：材料按配方自身的行宽索引，不按网格的行宽，
     * 否则两格宽的样板一次读三格，比的是错的列。
     */
    private static boolean fitsAt(
            List<ItemStack> cells,
            List<Optional<Ingredient>> ingredients,
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
                Optional<Ingredient> ingredient = ingredients.get(localY * width + column);
                // 被覆盖的格位没有材料条目就必须为空——那是样板自己的空隙标记。
                if (!Ingredient.testOptionalIngredient(ingredient, cell)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 无序配方的材料是否刚好用尽、没有剩余：走完整匹配，与工作台的 {@code RecipeMatcher} 相同，不是贪心扫描。
     * 橡木要占用「橡木木板」槽位。
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
