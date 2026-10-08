package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 把实时奥术配方变成样板：按产物、按已编码的样板、或按手工填好的网格。
 * 样板只存具体的显示物品堆，背后的配方每次重载都要重查。
 */
final class ArcanePatternLookup {

    private static final Ingredient EMPTY_INGREDIENT = Ingredient.of();

    private ArcanePatternLookup() {}

    /**
     * 把产出 {@code result} 的奥术配方转成样板；没有配方产出这个精确物品堆时返回 {@code null}。
     */
    static @Nullable ThEArcanePattern fromResult(@Nullable Level level, ItemStack result) {
        if (level == null || result.isEmpty()) {
            return null;
        }
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty() || !ItemStack.isSameItemSameComponents(output, result)) {
                continue;
            }
            ThEArcanePattern pattern = fromRecipe(arcane, output);
            if (pattern != null) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * 拿样板声称编码的奥术配方校验玩家编码的 [AE2] 样板。
     * @return 样板；没有奥术配方匹配时为 {@code null}
     */
    static @Nullable ThEArcanePattern fromEncoded(
            @Nullable Level level, List<ItemStack> patternInputs, ItemStack output) {
        if (level == null || output.isEmpty()) {
            return null;
        }
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack recipeOutput = holder.value().getResultItem(level.registryAccess());
            if (recipeOutput.isEmpty() || !ItemStack.isSameItemSameComponents(recipeOutput, output)) {
                continue;
            }
            ThEArcanePattern pattern = fromRecipe(arcane, recipeOutput);
            if (pattern != null && pattern.acceptsInputs(patternInputs)) {
                return pattern;
            }
        }
        return null;
    }

    static @Nullable ThEArcanePattern fromRecipe(IArcaneRecipe recipe, ItemStack output) {
        Layout layout = layoutOf(recipe);
        if (layout == null || layout.cells().isEmpty()) {
            return null;
        }
        ResearchGate gate = recipe.researchGate().orElse(null);
        return new ThEArcanePattern(
                output,
                layout.cells(),
                layout.ingredients(),
                layout.width(),
                layout.height(),
                recipe.crystalCost(),
                recipe.visCost(),
                gate == null ? null : gate.entry(),
                gate == null ? null : gate.stage().orElse(null),
                gridTags(layout));
    }

    /**
     * 查明手工填好的 3x3 网格代表哪个奥术配方。
     * @return 样板；没有配方对上这个网格时为 {@code null}
     */
    static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        if (level == null || cells.size() != ThEArcanePattern.MAX_GRID) {
            return null;
        }
        RecipeManager manager = level.getRecipeManager();
        ArcaneRecipeIndex.index(manager);

        // 按物品取交集，只有收得下全部现有物品的配方留下。
        // 索引里查不到的物品（索引由默认物品堆建）走全量扫描，不丢配方。
        Set<ResourceLocation> candidates = null;
        for (ItemStack cell : cells) {
            if (cell.isEmpty()) {
                continue;
            }
            Set<ResourceLocation> accepting = ArcaneRecipeIndex.accepting(cell.getItem());
            if (accepting == null) {
                candidates = null;
                break;
            }
            if (candidates == null) {
                candidates = new HashSet<>(accepting);
            } else {
                candidates.retainAll(accepting);
            }
            if (candidates.isEmpty()) {
                return null;
            }
        }

        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            if (candidates != null && !candidates.contains(holder.id())) {
                continue;
            }
            if (!ArcaneGridMatcher.satisfiesGrid(arcane, cells, level)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern pattern = fromRecipe(arcane, output);
            if (pattern != null) {
                return pattern;
            }
        }
        return null;
    }

    /**
     * 解析外部传进来的样板（[AE2] 编码终端或样板供应器）：它的条目是具体物品堆，按成员关系匹配，不逐格比。
     * @return 样板；没有配方同时对上产物与输入时为 {@code null}
     */
    static @Nullable ThEArcanePattern resolve(
            @Nullable Level level, List<ItemStack> inputs, ItemStack output) {
        if (level == null || output.isEmpty() || inputs.isEmpty()) {
            return null;
        }
        for (RecipeHolder<?> holder : level.getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack recipeOutput = holder.value().getResultItem(level.registryAccess());
            if (recipeOutput.isEmpty() || !ItemStack.isSameItemSameComponents(recipeOutput, output)) {
                continue;
            }
            ThEArcanePattern pattern = fromRecipe(arcane, recipeOutput);
            if (pattern != null && pattern.acceptsInputs(inputs)) {
                return pattern;
            }
        }
        return null;
    }

    private record Layout(List<ItemStack> cells, List<Ingredient> ingredients, int width, int height) {}

    /**
     * 推出奥术配方的格位布局：有序配方保留真实宽高，无序配方按阅读顺序排。
     * 显示物品堆和网格匹配用的材料一起留下。
     */
    private static @Nullable Layout layoutOf(IArcaneRecipe recipe) {
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            List<Optional<Ingredient>> optional = shaped.optionalIngredients();
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            if (width < 1 || height < 1 || width > ThEArcanePattern.GRID_SIDE
                    || height > ThEArcanePattern.GRID_SIDE) {
                return null;
            }
            if (optional.size() < width * height) {
                return null;
            }
            // 材料按配方自己的行排，网格固定三格宽：沿用配方的步长，两格宽配方的第二行会落进网格第一行。
            List<ItemStack> cells = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            List<Ingredient> ingredients = new ArrayList<>(width * height);
            for (int row = 0; row < ThEArcanePattern.GRID_SIDE; row++) {
                for (int column = 0; column < ThEArcanePattern.GRID_SIDE; column++) {
                    boolean inside = row < height && column < width;
                    Optional<Ingredient> entry =
                            inside ? optional.get(row * width + column) : Optional.empty();
                    cells.add(inside ? representative(entry) : ItemStack.EMPTY);
                }
            }
            for (int i = 0; i < width * height; i++) {
                ingredients.add(optional.get(i).orElse(EMPTY_INGREDIENT));
            }
            return new Layout(cells, ingredients, width, height);
        }
        if (recipe instanceof ArcaneShapelessCraftingRecipe shapeless) {
            List<Ingredient> ingredients = shapeless.ingredients();
            List<ItemStack> cells = new ArrayList<>(ingredients.size());
            for (Ingredient ingredient : ingredients) {
                cells.add(representative(Optional.of(ingredient)));
            }
            int width = Math.min(ThEArcanePattern.MAX_GRID, Math.max(1, cells.size()));
            return new Layout(cells, ingredients, width, 1);
        }
        return null;
    }

    /**
     * 每个材料取一个代表物品堆：样板只存具体物品堆，多物品材料取第一项，标签取显示物品。
     */
    private static ItemStack representative(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] items = ingredient.get().getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0].copy();
    }

    /**
     * 材料代表的物品标签，普通物品列表返回 {@code null}：配方说的是「任意铁锭」，
     * 写死列表第一个成员会让组装机拒收别的成员。
     */
    private static @Nullable TagKey<Item> tagOf(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return null;
        }
        // [Ingredient#getValues] 对非普通物品列表会抛异常，异常逃得出 [fromRecipe] 和 [resolveGrid]，
        // 摆得正确的网格会被报成「没有配方」。
        try {
            for (Ingredient.Value value : ingredient.get().getValues()) {
                if (value instanceof Ingredient.TagValue tagValue) {
                    return tagValue.tag();
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /**
     * 布局放在 3x3 网格上的材料标签：有序配方的列表压到它自己的 {@code width x height}，
     * 跟 {@code layoutOf} 处理显示物品堆一样。
     */
    private static List<TagKey<Item>> gridTags(Layout layout) {
        List<TagKey<Item>> byCell = new ArrayList<>(ThEArcanePattern.MAX_GRID);
        for (int row = 0; row < ThEArcanePattern.GRID_SIDE; row++) {
            for (int column = 0; column < ThEArcanePattern.GRID_SIDE; column++) {
                int index = row * layout.width() + column;
                boolean inside = row < layout.height() && column < layout.width();
                if (!inside || index < 0 || index >= layout.ingredients().size()) {
                    byCell.add(null);
                    continue;
                }
                byCell.add(tagOf(Optional.of(layout.ingredients().get(index))));
            }
        }
        return byCell;
    }
}
