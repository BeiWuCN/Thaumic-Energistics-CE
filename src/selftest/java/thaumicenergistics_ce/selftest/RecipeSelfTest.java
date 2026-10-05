package thaumicenergistics_ce.selftest;

import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.util.ThELog;

/**
 * Feeds every arcane recipe's own grid back through the machine's matcher; off by default because
 * it walks all 283 recipes. Each grid must resolve to its own recipe, so a mismatch names a side.
 */
public final class RecipeSelfTest {

    private RecipeSelfTest() {}

    public static void run(ServerStartedEvent event) {
        // Env var, not -D: a -D on the Gradle command line never reaches the game's JVM.
        if (!"true".equalsIgnoreCase(System.getenv("THAUMICENERGISTICS_RECIPE_SELFTEST"))) {
            return;
        }
        var level = event.getServer().overworld();
        if (level == null) {
            return;
        }
        int total = 0;
        int failed = 0;
        for (RecipeHolder<?> holder : event.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern pattern = ThEArcanePattern.fromRecipe(arcane, output);
            if (pattern == null) {
                continue;
            }
            total++;
            // The grid as the workbench holds it: the recipe's own cells in its own order.
            List<ItemStack> grid = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                grid.add(ItemStack.EMPTY);
            }
            List<ItemStack> cells = pattern.grid();
            for (int i = 0; i < cells.size() && i < ThEArcanePattern.MAX_GRID; i++) {
                grid.set(i, cells.get(i));
            }
            ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, grid);
            boolean ok = resolved != null
                    && ItemStack.isSameItemSameComponents(resolved.result(), output);
            if (ok) {
                continue;
            }
            failed++;
            boolean fromGrid = ThEArcanePattern.satisfiesGrid(arcane, grid, level);
            ThELog.LOG.info("[selftest] FAIL {} gridMatches={} grid={}", holder.id(), fromGrid, pattern.grid());
        }
        ThELog.LOG.info("[selftest] {} arcane recipes, {} resolved to themselves, {} failed",
                total, total - failed, failed);

        // JEI round trip: a broken transfer is indistinguishable from a broken matcher.
        int transfers = 0;
        int broken = 0;
        for (RecipeHolder<?> holder : event.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            List<ItemStack> template =
                    thaumicenergistics_ce.integration.jei.ArcaneRecipeTypes.templateFor(holder);
            if (template.isEmpty()) {
                continue;
            }
            transfers++;
            List<ItemStack> grid = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                grid.add(i < template.size() ? template.get(i) : ItemStack.EMPTY);
            }
            ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, grid);
            if (resolved != null && ItemStack.isSameItemSameComponents(resolved.result(), output)) {
                continue;
            }
            broken++;
            if (broken <= 10) {
                ThELog.LOG.info(
                        "[selftest] TRANSFER {} -> {} template={}",
                        holder.id(),
                        resolved == null ? "no match" : resolved.result().getItem().toString(),
                        template);
            }
        }
        ThELog.LOG.info("[selftest] {} JEI templates, {} read back, {} broken",
                transfers, transfers - broken, broken);

        // Loading a stored pattern writes only its own cells; the remaining grid cells stay empty.
        int loads = 0;
        int unreadable = 0;
        for (RecipeHolder<?> holder : event.getServer().getRecipeManager().getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            ItemStack output = holder.value().getResultItem(level.registryAccess());
            if (output.isEmpty()) {
                continue;
            }
            ThEArcanePattern pattern = ThEArcanePattern.fromRecipe(arcane, output);
            if (pattern == null) {
                continue;
            }
            loads++;
            // Mirrors MenuKnowledgeInscriber.loadStoredPattern: its cells first, rest empty.
            List<ItemStack> grid = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            List<ItemStack> cells = pattern.grid();
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                grid.add(i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
            }
            ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, grid);
            if (resolved != null && ItemStack.isSameItemSameComponents(resolved.result(), output)) {
                continue;
            }
            unreadable++;
            if (unreadable <= 10) {
                ThELog.LOG.info(
                        "[selftest] LOAD {} -> {} cells={}",
                        holder.id(),
                        resolved == null ? "no match" : resolved.result().getItem().toString(),
                        cells);
            }
        }
        ThELog.LOG.info("[selftest] {} stored patterns, {} read back, {} unreadable",
                loads, loads - unreadable, unreadable);
    }
}
