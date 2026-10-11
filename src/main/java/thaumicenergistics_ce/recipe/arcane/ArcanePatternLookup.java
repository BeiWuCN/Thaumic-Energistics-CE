package thaumicenergistics_ce.recipe.arcane;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.util.context.ContextMap;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

final class ArcanePatternLookup {

    private ArcanePatternLookup() {}

    static @Nullable ThEArcanePattern fromResult(@Nullable Level level, ItemStack result) {
        if (level == null || result.isEmpty()) {
            return null;
        }
        RecipeManager manager = recipes(level);
        if (manager == null) {
            return null;
        }
        ContextMap context = SlotDisplayContext.fromLevel(level);
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = outputOf(arcane, context);
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

    static @Nullable ThEArcanePattern fromEncoded(
            @Nullable Level level, List<ItemStack> patternInputs, ItemStack output) {
        if (level == null || output.isEmpty()) {
            return null;
        }
        RecipeManager manager = recipes(level);
        if (manager == null) {
            return null;
        }
        ContextMap context = SlotDisplayContext.fromLevel(level);
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack recipeOutput = outputOf(arcane, context);
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

    private static @Nullable RecipeManager recipes(@Nullable Level level) {
        if (level == null || level.isClientSide()) {
            return null;
        }
        MinecraftServer server = level.getServer();
        return server == null ? null : server.getRecipeManager();
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

    static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        if (level == null || cells.size() != ThEArcanePattern.MAX_GRID) {
            return null;
        }
        RecipeManager manager = recipes(level);
        if (manager == null) {
            return null;
        }
        ArcaneRecipeIndex.index(manager);
        ContextMap context = SlotDisplayContext.fromLevel(level);

        Set<ResourceKey<Recipe<?>>> candidates = null;
        for (ItemStack cell : cells) {
            if (cell.isEmpty()) {
                continue;
            }
            Set<ResourceKey<Recipe<?>>> accepting = ArcaneRecipeIndex.accepting(cell.getItem());
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
            ItemStack output = outputOf(arcane, context);
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

    static @Nullable ThEArcanePattern resolve(
            @Nullable Level level, List<ItemStack> inputs, ItemStack output) {
        if (level == null || output.isEmpty() || inputs.isEmpty()) {
            return null;
        }
        RecipeManager manager = recipes(level);
        if (manager == null) {
            return null;
        }
        ContextMap context = SlotDisplayContext.fromLevel(level);
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack recipeOutput = outputOf(arcane, context);
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

    private static ItemStack outputOf(IArcaneRecipe recipe, ContextMap context) {
        for (RecipeDisplay display : recipe.display()) {
            ItemStack stack = display.result().resolveForFirstStack(context);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private record Layout(List<ItemStack> cells, List<Optional<Ingredient>> ingredients, int width, int height) {}

    public static List<Optional<Ingredient>> gridAlignedIngredients(IArcaneRecipe recipe) {
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            List<Optional<Ingredient>> optional = shaped.getIngredients();
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            if (width < 1 || height < 1 || width > ThEArcanePattern.GRID_SIDE
                    || height > ThEArcanePattern.GRID_SIDE || optional.size() < width * height) {
                return List.of();
            }
            List<Optional<Ingredient>> ingredients = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int row = 0; row < ThEArcanePattern.GRID_SIDE; row++) {
                for (int column = 0; column < ThEArcanePattern.GRID_SIDE; column++) {
                    boolean inside = row < height && column < width;
                    ingredients.add(inside ? optional.get(row * width + column) : Optional.empty());
                }
            }
            return ingredients;
        }
        if (recipe instanceof ArcaneShapelessCraftingRecipe shapeless) {
            List<Optional<Ingredient>> ingredients =
                    shapeless.ingredients().stream().map(Optional::of).toList();
            if (ingredients.size() > ThEArcanePattern.MAX_GRID) {
                return List.of();
            }
            List<Optional<Ingredient>> padded = new ArrayList<>(ingredients);
            while (padded.size() < ThEArcanePattern.MAX_GRID) {
                padded.add(Optional.empty());
            }
            return padded;
        }
        return List.of();
    }

    private static @Nullable Layout layoutOf(IArcaneRecipe recipe) {
        List<Optional<Ingredient>> aligned = gridAlignedIngredients(recipe);
        if (aligned.isEmpty()) {
            return null;
        }
        List<ItemStack> cells = new ArrayList<>(ThEArcanePattern.MAX_GRID);
        for (Optional<Ingredient> entry : aligned) {
            cells.add(representative(entry));
        }
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            return new Layout(cells, aligned, shaped.getWidth(), shaped.getHeight());
        }
        int width = Math.min(ThEArcanePattern.MAX_GRID, Math.max(1, aligned.size()));
        return new Layout(cells, aligned, width, 1);
    }

    private static ItemStack representative(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            ItemStack shown = ingredient.get()
                    .display()
                    .resolveForFirstStack(new ContextMap.Builder().create(SlotDisplayContext.CONTEXT));
            if (!shown.isEmpty()) {
                return shown;
            }
        } catch (RuntimeException e) {
        }
        return ingredient.get().items().findFirst()
                .map(holder -> new ItemStack(holder.value()))
                .orElse(ItemStack.EMPTY);
    }

    private static @Nullable TagKey<Item> tagOf(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return null;
        }
        try {
            return ingredient.get().getValues().unwrapKey().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

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
                byCell.add(tagOf(layout.ingredients().get(index)));
            }
        }
        return byCell;
    }
}
