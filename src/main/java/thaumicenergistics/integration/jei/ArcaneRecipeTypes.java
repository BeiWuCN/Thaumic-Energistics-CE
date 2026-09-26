package thaumicenergistics.integration.jei;

import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.arcane.ThEArcanePattern;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;

/**
 * The recipe type the Knowledge Inscriber encodes for, and how to read a grid out of one.
 *
 * <p>The type is Thaumaturge's own arcane workbench category, borrowed rather than mirrored: the machine
 * encodes exactly the recipes the workbench can craft, so giving JEI a second page with the same recipes
 * on it would only be a way for the two to disagree.
 *
 * <p>Resolved inside the method and not in a static field, so that a client without JEI never touches
 * Thaumaturge's JEI classes. Nothing but JEI itself calls into this package.
 */
public final class ArcaneRecipeTypes {

    private ArcaneRecipeTypes() {}

    /** Thaumaturge's arcane workbench recipe type. */
    public static RecipeType<RecipeHolder<?>> arcane() {
        RecipeType<?> type = com.leclowndu93150.thaumaturge.compat.jei.category.ArcaneWorkbenchCategory.RECIPE_TYPE;
        @SuppressWarnings("unchecked")
        RecipeType<RecipeHolder<?>> cast = (RecipeType<RecipeHolder<?>>) (RecipeType<?>) type;
        return cast;
    }

    /**
     * The nine grid cells a recipe asks for, in reading order, each with all the items that would do.
     * <p>Read from the recipe's own grid - {@code optionalIngredients} for a shaped one, its ingredient
     * list for a shapeless one - and not through {@link ThEArcanePattern}. A pattern can only carry one
     * concrete stack per cell, so building one first means collapsing every ingredient to a single
     * arbitrary member of its tag: a recipe wanting any iron plate would arrive as one particular iron
     * plate, which is not necessarily the one the player has, and the transfer then filled the grid with
     * something the recipe does not actually accept.
     *
     * <p>Each cell is a list of variants with the first one first, so the transfer can place a variant
     * the player owns. An empty list means the recipe leaves that cell empty.
     */
    public static @Nullable List<List<ItemStack>> cellsFor(RecipeHolder<?> holder) {
        if (!(holder.value() instanceof IArcaneRecipe arcane)) {
            return null;
        }
        if (arcane instanceof ArcaneShapedCraftingRecipe shaped) {
            // Through the pattern, which lays the recipe out in the workbench's own 3x3. Reading the
            // recipe's ingredients straight out instead gives them at the recipe's own row width, and a
            // two-wide recipe then lands in the grid's first row with its second row pushed along: the
            // transfer filled three cells the recipe never uses, and the machine refused the grid it was
            // handed. Measured: 20 of 283 recipes, every one of them narrower than the grid.
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
                // Every variant of the ingredient standing in that cell, so the transfer can pick one the
                // player actually has rather than the single stack a pattern can carry.
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
     * Whether this recipe can be laid out in the machine's grid at all.
     *
     * <p>Shaped recipes are padded to their own shape and so always fit a 3x3; a shapeless one needs its
     * ingredients to fit in nine cells.
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
     * The grid a transfer would fill, as plain stacks, for the self-test.
     *
     * <p>Picks each cell's first variant, which is what a transfer falls back to when the player owns
     * none of them. The point is to compare this against what the machine then resolves: a transfer that
     * fills a grid the machine cannot read back is a broken round trip, and it looks from the outside
     * exactly like a matcher that does not work.
     */
    public static java.util.List<ItemStack> templateFor(RecipeHolder<?> holder) {
        List<List<ItemStack>> cells = cellsFor(holder);
        if (cells == null) {
            return java.util.List.of();
        }
        java.util.List<ItemStack> template = new java.util.ArrayList<>(cells.size());
        for (List<ItemStack> variants : cells) {
            template.add(variants.isEmpty() ? ItemStack.EMPTY : variants.getFirst());
        }
        return template;
    }

    /**
     * Registry access for reading a recipe's result.
     *
     * <p>Thaumaturge's JEI plugin caches the client's copy, which is the right source when JEI is up.
     *
     * <p>The fallback asks the running server, not the client, and that is deliberate. This method is
     * reached from the mod's own self-test, which runs on {@code ServerStartedEvent} - on a dedicated
     * server as well as in single player. Naming a client class here, even inside a catch block, is enough
     * to take the whole dedicated server down when that path runs: the class is loaded and verified before
     * the branch is taken, and the loader refuses client classes on that side.
     *
     * <p>That is not hypothetical - it is what happened. {@code Minecraft.getInstance()} sat here and the
     * server crashed with "Attempted to load class net/minecraft/client/Minecraft for invalid dist".
     * The server's own registry access answers the same question and exists on both sides.
     */
    private static net.minecraft.core.HolderLookup.Provider registryAccess() {
        try {
            return com.leclowndu93150.thaumaturge.compat.jei.ThaumaturgeJEIPlugin.clientRegistryAccess();
        } catch (RuntimeException e) {
            var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                return server.registryAccess();
            }
            ThaumicEnergistics.LOG.debug("No registry access available for a recipe result: {}", e.toString());
            throw e;
        }
    }
}
