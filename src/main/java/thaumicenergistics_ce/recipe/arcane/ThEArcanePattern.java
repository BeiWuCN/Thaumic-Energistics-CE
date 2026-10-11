package thaumicenergistics_ce.recipe.arcane;

import net.minecraft.core.Holder;
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
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

public record ThEArcanePattern(
        ItemStack result,
        List<ItemStack> grid,
        List<Optional<Ingredient>> ingredients,
        int gridWidth,
        int gridHeight,
        AspectList crystals,
        int baseVis,
        @Nullable Identifier research,
        @Nullable Integer researchStage,
        List<TagKey<Item>> cellTags) {

    public static final int CRYSTAL_SUBSTITUTE_VIS = 2;

    public static final float CRAFT_AURA_SURCHARGE = 1.25F;

    public static final int MAX_GRID = 9;

    public static final int GRID_SIDE = 3;

    public ThEArcanePattern {
        result = result.copy();
        grid = List.copyOf(grid);
        ingredients = List.copyOf(ingredients);
        cellTags = Collections.unmodifiableList(new ArrayList<>(cellTags));
    }

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
        for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            choices.add(new ItemStack(holder.value()));
        }
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


    public static @Nullable ThEArcanePattern fromResult(@Nullable Level level, ItemStack result) {
        return ArcanePatternLookup.fromResult(level, result);
    }

    public static @Nullable ThEArcanePattern fromEncoded(
            @Nullable Level level, List<ItemStack> patternInputs, ItemStack output) {
        return ArcanePatternLookup.fromEncoded(level, patternInputs, output);
    }

    public boolean acceptsInputs(List<ItemStack> inputs) {
        return ArcaneGridMatcher.acceptsInputs(grid, cellTags, crystalItems(), inputs);
    }

    public static @Nullable ThEArcanePattern fromRecipe(IArcaneRecipe recipe, ItemStack output) {
        return ArcanePatternLookup.fromRecipe(recipe, output);
    }

    public static List<Optional<Ingredient>> gridAlignedIngredients(IArcaneRecipe recipe) {
        return ArcanePatternLookup.gridAlignedIngredients(recipe);
    }

    public static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        return ArcanePatternLookup.resolveGrid(level, cells);
    }

    public static boolean satisfiesGrid(IArcaneRecipe recipe, List<ItemStack> cells, @Nullable Level level) {
        return ArcaneGridMatcher.satisfiesGrid(recipe, cells, level);
    }

    public static @Nullable ThEArcanePattern resolve(
            @Nullable Level level, List<ItemStack> inputs, ItemStack output) {
        return ArcanePatternLookup.resolve(level, inputs, output);
    }


    public AspectList resolveCrystals(HolderLookup.Provider registries) {
        return ArcaneResearchGate.resolveCrystals(crystals, registries);
    }

    public List<ResourceKey<IAspect>> crystalKeys() {
        return ArcaneResearchGate.crystalKeys(crystals);
    }

    public ItemStack toItem(HolderLookup.Provider registries) {
        return ArcanePatternTags.toItem(this, registries);
    }

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
