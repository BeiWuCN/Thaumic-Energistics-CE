package thaumicenergistics_ce.integration.jei;

import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.util.context.ContextMap;
import java.util.Optional;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import thaumicenergistics_ce.integration.ae2.ClientRegistries;
import thaumicenergistics_ce.util.ThELog;
import thaumicenergistics_ce.recipe.IngredientStacks;

public final class ArcaneRecipeTypes {

    private ArcaneRecipeTypes() {}

    public static @Nullable List<List<ItemStack>> cellsFor(RecipeHolder<?> holder) {
        if (!(holder.value() instanceof IArcaneRecipe arcane)) {
            return null;
        }
        if (arcane instanceof ArcaneShapedCraftingRecipe) {
            ItemStack output = outputOf(arcane);
            if (output.isEmpty()) {
                return null;
            }
            ThEArcanePattern pattern = ThEArcanePattern.fromRecipe(arcane, output);
            if (pattern == null) {
                return null;
            }
            List<Optional<Ingredient>> ingredients = ThEArcanePattern.gridAlignedIngredients(arcane);
            List<ItemStack> grid = pattern.grid();
            List<List<ItemStack>> cells = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                ItemStack representative = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
                if (representative.isEmpty()) {
                    cells.add(List.of());
                    continue;
                }
                List<ItemStack> variants =
                        i < ingredients.size() ? displayVariants(ingredients.get(i)) : List.of();
                if (variants.isEmpty()) {
                    variants = List.of(representative.copyWithCount(1));
                }
                cells.add(variants);
            }
            return cells;
        }
        if (arcane instanceof ArcaneShapelessCraftingRecipe) {
            List<List<ItemStack>> cells = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (Optional<Ingredient> ingredient : ThEArcanePattern.gridAlignedIngredients(arcane)) {
                cells.add(displayVariants(ingredient));
            }
            while (cells.size() < ThEArcanePattern.MAX_GRID) {
                cells.add(List.of());
            }
            return cells;
        }
        return null;
    }

    private static List<ItemStack> displayVariants(Optional<Ingredient> ingredient) {
        return ingredient.map(value -> IngredientStacks.variants(value, registryAccess())).orElseGet(List::of);
    }

    public static boolean fitsGrid(RecipeHolder<?> holder) {
        List<List<ItemStack>> cells = cellsFor(holder);
        if (cells == null) {
            return false;
        }
        int used = 0;
        for (List<ItemStack> cell : cells) {
            if (!cell.isEmpty()) {
                used++;
            }
        }
        return used > 0;
    }

    public static List<ItemStack> templateFor(RecipeHolder<?> holder) {
        List<List<ItemStack>> cells = cellsFor(holder);
        if (cells == null) {
            return List.of();
        }
        List<ItemStack> template = new ArrayList<>(cells.size());
        for (List<ItemStack> variants : cells) {
            template.add(variants.isEmpty() ? ItemStack.EMPTY : variants.getFirst());
        }
        return template;
    }

    private static HolderLookup.Provider registryAccess() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return ClientRegistries.get();
    }

    private static ItemStack outputOf(IArcaneRecipe recipe) {
        ContextMap context = new ContextMap.Builder()
                .withOptionalParameter(SlotDisplayContext.REGISTRIES, registryAccess())
                .create(SlotDisplayContext.CONTEXT);
        for (RecipeDisplay display : recipe.display()) {
            ItemStack stack = display.result().resolveForFirstStack(context);
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }
}
