package thaumicenergistics_ce.integration.jei;

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
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.integration.ae2.ClientRegistries;
import thaumicenergistics_ce.util.ThELog;

/**
 * How to read a Thaumaturge arcane recipe's grid: the nine cells a transfer would fill, and whether one
 * fits. It names no JEI type, because a dedicated server has no JEI to borrow one from; that is
 * {@code ArcaneJeiRecipeType}'s job.
 */
public final class ArcaneRecipeTypes {

    private ArcaneRecipeTypes() {}

    /**
     * The nine grid cells a recipe asks for, in reading order, as variant lists; an empty list is an
     * empty cell. Read from the pattern: it carries one stack per cell, not the full ingredient.
     */
    public static @Nullable List<List<ItemStack>> cellsFor(RecipeHolder<?> holder) {
        if (!(holder.value() instanceof IArcaneRecipe arcane)) {
            return null;
        }
        if (arcane instanceof ArcaneShapedCraftingRecipe shaped) {
            // Pattern's 3x3 layout: reading ingredients directly shifts a two-wide row - 20 of 283.
            ItemStack output = holder.value().getResultItem(registryAccess());
            if (output.isEmpty()) {
                return null;
            }
            ThEArcanePattern pattern = ThEArcanePattern.fromRecipe(arcane, output);
            if (pattern == null) {
                return null;
            }
            List<Ingredient> ingredients = shaped.getIngredients();
            List<ItemStack> grid = pattern.grid();
            List<List<ItemStack>> cells = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                ItemStack representative = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
                if (representative.isEmpty()) {
                    cells.add(List.of());
                    continue;
                }
                // Every variant of the ingredient in that cell, so the transfer can pick one the player has.
                List<ItemStack> variants = new ArrayList<>();
                for (Ingredient ingredient : ingredients) {
                    if (ingredient.test(representative)) {
                        for (ItemStack item : ingredient.getItems()) {
                            if (!item.isEmpty()) {
                                variants.add(item.copyWithCount(1));
                            }
                        }
                        break;
                    }
                }
                if (variants.isEmpty()) {
                    variants.add(representative.copyWithCount(1));
                }
                cells.add(variants);
            }
            return cells;
        }
        if (arcane instanceof ArcaneShapelessCraftingRecipe shapeless) {
            List<List<ItemStack>> cells = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (Ingredient ingredient : shapeless.getIngredients()) {
                List<ItemStack> variants = new ArrayList<>();
                if (ingredient != null && !ingredient.isEmpty()) {
                    for (ItemStack item : ingredient.getItems()) {
                        if (!item.isEmpty()) {
                            variants.add(item.copyWithCount(1));
                        }
                    }
                }
                cells.add(variants);
            }
            while (cells.size() < ThEArcanePattern.MAX_GRID) {
                cells.add(List.of());
            }
            return cells;
        }
        return null;
    }

    /**
     * Whether this recipe fits the machine's grid: shaped recipes are padded to their own shape and
     * always fit a 3x3; a shapeless one needs its ingredients to fit in nine cells.
     */
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

    /**
     * Registry access for a recipe's result: the server's when there is one, else this side's own through
     * the client sink. Never Thaumaturge's JEI plugin, which a dedicated server cannot load.
     */
    private static HolderLookup.Provider registryAccess() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return ClientRegistries.get();
    }
}
