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
 * 把实时奥术配方变成样板：可按产物、按已编码的样板，或按手工填好的网格。
 * 样板只保存具体的显示物品堆，因此每次重载都会重新查找它背后的配方。
 */
final class ArcanePatternLookup {

    private static final Ingredient EMPTY_INGREDIENT = Ingredient.of();

    private ArcanePatternLookup() {}

    /**
     * 把产出 {@code result} 的奥术配方转换为样板；若没有任何配方产出该
     * 精确物品堆，则返回 {@code null}。
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
     * 用玩家编码的 AE2 样板所声称编码的奥术配方来校验该样板。
     * @return 样板；没有奥术配方匹配时返回 {@code null}
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
                recipe.getCrystals(),
                recipe.getBaseVis(),
                gate == null ? null : gate.entry(),
                gate == null ? null : gate.stage().orElse(null),
                gridTags(layout));
    }

    /**
     * 找出手工填好的 3x3 网格所代表的奥术配方。
     * @return 样板；没有奥术配方匹配该网格时返回 {@code null}
     */
    static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        if (level == null || cells.size() != ThEArcanePattern.MAX_GRID) {
            return null;
        }
        RecipeManager manager = level.getRecipeManager();
        ArcaneRecipeIndex.index(manager);

        // 对各物品的候选集合取交集：只有接受全部现有物品的配方才留下。某个物品不在
        // 索引中（索引由默认物品堆构建）时回退到全量扫描，绝不会丢配方。
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
     * 解析外部传入的样板——AE2 的编码终端或样板供应器：它的条目是具体物品堆，
     * 所以按成员关系匹配，而不是逐格匹配。
     * @return 样板；没有奥术配方同时匹配产物与输入时返回 {@code null}
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
     * 推导奥术配方的格位布局：有序配方保留真实的宽高，无序配方按
     * 阅读顺序排布。同时保留显示物品堆和网格匹配所用的材料。
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
            // 材料按配方自身的行来排列，而网格固定三格宽：若沿用配方的步长，
            // 两格宽配方的第二行会落进网格的第一行。
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
     * 为每个材料取一个代表性物品堆：样板只保存具体物品堆，因此多物品材料取
     * 它的第一个条目，标签则取显示物品。
     */
    private static ItemStack representative(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] items = ingredient.get().getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0].copy();
    }

    /**
     * 材料所代表的物品标签；只是普通物品列表时返回 {@code null}：配方表达的是
     * 「任意铁锭」，而写入列表中第一个成员曾使组装机拒绝另一个成员。
     */
    private static @Nullable TagKey<Item> tagOf(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return null;
        }
        // 已加保护：[Ingredient#getValues] 对非普通物品列表会抛异常，该异常会逃出
        // [fromRecipe] 与 [resolveGrid]，让一个正确摆放的网格被报成「没有配方」。
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
     * 布局在 3x3 网格上的材料标签：有序配方的列表会压缩到它自身的
     * {@code width x height}，与 {@code layoutOf} 对显示物品堆的处理一致。
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
