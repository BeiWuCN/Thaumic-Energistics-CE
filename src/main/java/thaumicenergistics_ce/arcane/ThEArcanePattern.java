package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * One resolved Thaumaturge arcane crafting job: grid layout, vis price, primal crystals. It is
 * derived from the live {@link RecipeManager}, so datapack edits apply on the next reload, and only
 * {@link #save}/{@link #load} round-trip one into a knowledge core.
 */
public record ThEArcanePattern(
        ItemStack result,
        List<ItemStack> grid,
        List<Ingredient> ingredients,
        int gridWidth,
        int gridHeight,
        AspectList crystals,
        int baseVis,
        @Nullable ResourceLocation research,
        @Nullable Integer researchStage,
        List<TagKey<Item>> cellTags) {

    /** Vis per primal crystal consumed, matching Thaumaturge's own wand-substitution rate. */
    public static final int CRYSTAL_SUBSTITUTE_VIS = 2;

    /** Surcharge for paying with ambient vis instead of a wand, mirroring Thaumaturge. */
    public static final float CRAFT_AURA_SURCHARGE = 1.25F;

    public static final int MAX_GRID = 9;

    public static final int GRID_SIDE = 3;

    public ThEArcanePattern {
        result = result.copy();
        grid = List.copyOf(grid);
        ingredients = List.copyOf(ingredients);
        cellTags = Collections.unmodifiableList(new ArrayList<>(cellTags));
    }

    /**
     * The tag a grid cell stands for, or {@code null} for a plain item: carried because a pattern from
     * a core has no recipe behind it - see {@link #load}.
     */
    public @Nullable TagKey<Item> cellTag(int cell) {
        if (cell < 0 || cell >= cellTags.size()) {
            return null;
        }
        return cellTags.get(cell);
    }

    public List<ItemStack> cellChoices(int cell) {
        if (cell < 0 || cell >= grid.size()) {
            return List.of();
        }
        ItemStack display = grid.get(cell);
        TagKey<Item> tag = cellTag(cell);
        if (tag == null) {
            return display.isEmpty() ? List.of() : List.of(display);
        }
        List<ItemStack> choices = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(tag).ifPresent(holders -> holders.forEach(holder -> {
            choices.add(new ItemStack(holder.value()));
        }));
        if (choices.isEmpty() && !display.isEmpty()) {
            choices.add(display);
        }
        return choices;
    }

    public int crystalVis() {
        return ArcaneVisCost.crystalVis(crystals);
    }

    public int totalVis() {
        return ArcaneVisCost.totalVis(baseVis, crystals);
    }

    public int chargedVis() {
        return ArcaneVisCost.chargedVis(baseVis, crystals);
    }

    /**
     * The crystals supplied as items: the non-primal ones. A compound crystal counts as its primals,
     * which Thaumaturge does not substitute.
     */
    public AspectList crystalItems() {
        return ArcaneVisCost.nonPrimalCrystals(crystals);
    }

    public AspectList primalCrystals() {
        return ArcaneVisCost.primalCrystals(crystals);
    }

    public AspectList nonPrimalCrystals() {
        return ArcaneVisCost.nonPrimalCrystals(crystals);
    }

    public static boolean isGridEmpty(List<ItemStack> cells) {
        for (ItemStack cell : cells) {
            if (!cell.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public boolean isResearchGated() {
        return ArcaneResearchGate.isGated(research);
    }

    public Optional<ResearchGate> gate() {
        return ArcaneResearchGate.gate(research, researchStage);
    }

    public int cellCount() {
        return ArcaneResearchGate.cellCount(grid);
    }

    // ----- Resolution against the live recipe manager -----

    /**
     * Converts the arcane recipe producing {@code result} into a pattern, or {@code null} when none
     * produces that exact stack.
     */
    public static @Nullable ThEArcanePattern fromResult(@Nullable Level level, ItemStack result) {
        return ArcanePatternLookup.fromResult(level, result);
    }

    /**
     * Validates a player-encoded AE2 pattern against the arcane recipe it claims to encode.
     * @return the pattern, or {@code null} when no arcane recipe matches
     */
    public static @Nullable ThEArcanePattern fromEncoded(
            @Nullable Level level, List<ItemStack> patternInputs, ItemStack output) {
        return ArcanePatternLookup.fromEncoded(level, patternInputs, output);
    }

    /**
     * Whether every non-empty {@code inputs} entry is consumed as a grid cell or a crystal: multiplicity
     * is not checked, the live recipe is re-matched before the craft.
     */
    public boolean acceptsInputs(List<ItemStack> inputs) {
        return ArcaneGridMatcher.acceptsInputs(grid, cellTags, crystalItems(), inputs);
    }

    public static @Nullable ThEArcanePattern fromRecipe(IArcaneRecipe recipe, ItemStack output) {
        return ArcanePatternLookup.fromRecipe(recipe, output);
    }

    /**
     * Finds the arcane recipe a hand-filled 3x3 grid stands for.
     * @return the pattern, or {@code null} when no arcane recipe matches that grid
     */
    public static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        return ArcanePatternLookup.resolveGrid(level, cells);
    }

    public static boolean satisfiesGrid(IArcaneRecipe recipe, List<ItemStack> cells, @Nullable Level level) {
        return ArcaneGridMatcher.satisfiesGrid(recipe, cells, level);
    }

    /**
     * Resolves a pattern handed in from outside - AE2's encoding terminal or a pattern provider: its
     * entries are concrete stacks, so matching is by membership, not cell by cell.
     * @return the pattern, or {@code null} when no arcane recipe matches both output and inputs
     */
    public static @Nullable ThEArcanePattern resolve(
            @Nullable Level level, List<ItemStack> inputs, ItemStack output) {
        return ArcanePatternLookup.resolve(level, inputs, output);
    }

    // ----- Live registry helpers -----

    public AspectList resolveCrystals(HolderLookup.Provider registries) {
        return ArcaneResearchGate.resolveCrystals(crystals, registries);
    }

    public List<ResourceKey<IAspect>> crystalKeys() {
        return ArcaneResearchGate.crystalKeys(crystals);
    }

    /**
     * Wraps a pattern into the item AE2's CPU saves and decodes: the same {@link #save}/{@link #load}
     * contract as the knowledge core, so a pattern cannot differ between a core and a task list.
     */
    public ItemStack toItem(HolderLookup.Provider registries) {
        return ArcanePatternTags.toItem(this, registries);
    }

    /** Reads a pattern back out of the item {@link #toItem} produced, or {@code null} if unreadable. */
    public static @Nullable ThEArcanePattern ofItem(ItemStack stack, HolderLookup.Provider registries) {
        return ArcanePatternTags.ofItem(stack, registries);
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        return ArcanePatternTags.save(this, registries);
    }

    public static @Nullable ThEArcanePattern load(HolderLookup.Provider registries, CompoundTag tag) {
        return ArcanePatternTags.load(registries, tag);
    }
}
