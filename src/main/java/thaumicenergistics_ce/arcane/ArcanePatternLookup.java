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
 * Turns live arcane recipes into patterns: by result, by an encoded pattern, or by a hand-filled grid.
 * A pattern holds concrete display stacks, so the recipe behind it is looked up again on every reload.
 */
final class ArcanePatternLookup {

    private static final Ingredient EMPTY_INGREDIENT = Ingredient.of();

    private ArcanePatternLookup() {}

    /**
     * Converts the arcane recipe producing {@code result} into a pattern, or {@code null} when none
     * produces that exact stack.
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
     * Validates a player-encoded AE2 pattern against the arcane recipe it claims to encode.
     * @return the pattern, or {@code null} when no arcane recipe matches
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
     * Finds the arcane recipe a hand-filled 3x3 grid stands for.
     * @return the pattern, or {@code null} when no arcane recipe matches that grid
     */
    static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        if (level == null || cells.size() != ThEArcanePattern.MAX_GRID) {
            return null;
        }
        RecipeManager manager = level.getRecipeManager();
        ArcaneRecipeIndex.index(manager);

        // Intersect the per-item sets: only recipes taking every item present survive. An item missing from
        // the index (built from default stacks) falls back to a full scan, never to a lost recipe.
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
     * Resolves a pattern handed in from outside - AE2's encoding terminal or a pattern provider: its
     * entries are concrete stacks, so matching is by membership, not cell by cell.
     * @return the pattern, or {@code null} when no arcane recipe matches both output and inputs
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
     * Derives the cell layout of an arcane recipe: shaped keeps its real width and height, shapeless is
     * laid out in reading order. Keeps the display stacks and the ingredients a grid matches on.
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
            // Ingredients come in the recipe's own rows while the grid is three wide: using the recipe's
            // stride would put a two-wide recipe's second row in the grid's first.
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
     * A representative stack per ingredient: a pattern carries concrete stacks only, so a multi-item
     * ingredient takes its first entry, and a tag the <em>display</em> item.
     */
    private static ItemStack representative(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack[] items = ingredient.get().getItems();
        return items.length == 0 ? ItemStack.EMPTY : items[0].copy();
    }

    /**
     * The item tag an ingredient stands for, or {@code null} for a plain list of items: the recipe means
     * "any iron ingot", and writing the first listed member made an assembler refuse another member.
     */
    private static @Nullable TagKey<Item> tagOf(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return null;
        }
        // Guarded: Ingredient#getValues throws for anything but a plain item list, and the throw escapes
        // fromRecipe and resolveGrid, reporting "no recipe" for a perfectly laid out grid.
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
     * The layout's ingredient tags on the 3x3 grid: a shaped recipe's list is compacted to its own
     * {@code width x height}, as {@code layoutOf} does for the display stacks.
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
