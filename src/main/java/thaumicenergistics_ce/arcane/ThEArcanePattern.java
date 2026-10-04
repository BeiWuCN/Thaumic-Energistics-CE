package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * One resolved Thaumaturge arcane crafting job: grid layout, vis price, primal crystals.
 * <ul>
 *   <li>Derived from the live {@link RecipeManager}: datapack edits apply on the next reload.
 *   <li>Only {@link #save}/{@link #load} round-trip one into a knowledge core.
 * </ul>
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

    // ----- Candidate narrowing, for the inscriber's grid -----

    private static final Map<Item, Set<ResourceLocation>> ITEM_RECIPES = new HashMap<>();

    private static RecipeManager indexedManager;

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
        return primalCrystals().totalAmount() * CRYSTAL_SUBSTITUTE_VIS;
    }

    public int totalVis() {
        return Math.max(0, baseVis) + crystalVis();
    }

    public int chargedVis() {
        float modifier = crystals.entries().isEmpty() ? 1.0F : CRAFT_AURA_SURCHARGE;
        return (int) Math.ceil(totalVis() * modifier);
    }

    /**
     * The crystals supplied as items: the non-primal ones. A compound crystal counts as its primals,
     * which Thaumaturge does not substitute.
     */
    public AspectList crystalItems() {
        return nonPrimalCrystals();
    }

    public AspectList primalCrystals() {
        return filterCrystals(true);
    }

    public AspectList nonPrimalCrystals() {
        return filterCrystals(false);
    }

    private AspectList filterCrystals(boolean primal) {
        AspectList filtered = AspectList.EMPTY;
        for (AspectInstance entry : crystals.entries()) {
            if (entry.aspect().value().isPrimal() == primal) {
                filtered = filtered.add(entry.aspect(), entry.amount());
            }
        }
        return filtered;
    }

    public static boolean isGridEmpty(List<ItemStack> cells) {
        for (ItemStack cell : cells) {
            if (!cell.isEmpty()) {
                return false;
            }
        }
        return true;
    }


    private static final Ingredient EMPTY_INGREDIENT = Ingredient.of();

    public boolean isResearchGated() {
        return research != null;
    }

    public Optional<ResearchGate> gate() {
        if (research == null) {
            return Optional.empty();
        }
        return Optional.of(new ResearchGate(research, Optional.ofNullable(researchStage), false));
    }

    public int cellCount() {
        return grid.size();
    }

    // ----- Resolution against the live recipe manager -----

    /**
     * Converts the arcane recipe producing {@code result} into a pattern, or {@code null} when none
     * produces that exact stack.
     */
    public static @Nullable ThEArcanePattern fromResult(@Nullable Level level, ItemStack result) {
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
    public static @Nullable ThEArcanePattern fromEncoded(
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

    /**
     * Whether every non-empty {@code inputs} entry is consumed as a grid cell or a crystal: multiplicity
     * is not checked, the live recipe is re-matched before the craft.
     */
    public boolean acceptsInputs(List<ItemStack> inputs) {
        int matched = 0;
        for (ItemStack input : inputs) {
            if (input.isEmpty()) {
                continue;
            }
            if (!matchesGridCell(input) && !matchesCrystalItem(input)) {
                return false;
            }
            matched++;
        }
        return matched > 0;
    }

    private boolean matchesGridCell(ItemStack input) {
        for (int cell = 0; cell < grid.size(); cell++) {
            ItemStack stored = grid.get(cell);
            if (stored.isEmpty()) {
                continue;
            }
            TagKey<Item> tag = cellTag(cell);
            if (tag != null) {
                if (input.is(tag)) {
                    return true;
                }
                continue;
            }
            if (ItemStack.isSameItemSameComponents(stored, input)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCrystalItem(ItemStack input) {
        for (AspectInstance crystal : crystalItems().entries()) {
            ItemStack wanted = TcRegistry.crystalFor(crystal.aspect(), crystal.amount());
            if (!wanted.isEmpty() && ItemStack.isSameItemSameComponents(wanted, input)) {
                return true;
            }
        }
        return false;
    }

    public static @Nullable ThEArcanePattern fromRecipe(IArcaneRecipe recipe, ItemStack output) {
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
     * The layout's ingredient tags on the 3x3 grid: a shaped recipe's list is compacted to its own
     * {@code width x height}, as {@code layoutOf} does for the display stacks.
     */
    private static List<TagKey<Item>> gridTags(Layout layout) {
        List<TagKey<Item>> byCell = new ArrayList<>(MAX_GRID);
        for (int row = 0; row < GRID_SIDE; row++) {
            for (int column = 0; column < GRID_SIDE; column++) {
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

    /**
     * Finds the arcane recipe a hand-filled 3x3 grid stands for.
     * @return the pattern, or {@code null} when no arcane recipe matches that grid
     */
    public static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        if (level == null || cells.size() != MAX_GRID) {
            return null;
        }
        RecipeManager manager = level.getRecipeManager();
        indexRecipes(manager);

        // Intersect the per-item sets: only recipes taking every item present survive. An item missing from
        // the index (built from default stacks) falls back to a full scan, never to a lost recipe.
        Set<ResourceLocation> candidates = null;
        for (ItemStack cell : cells) {
            if (cell.isEmpty()) {
                continue;
            }
            Set<ResourceLocation> accepting = ITEM_RECIPES.get(cell.getItem());
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
            if (!satisfiesGrid(arcane, cells, level)) {
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
     * Rebuilds the index when the manager changes - a reload hands out a new one. An item is indexed by
     * any accepting ingredient: over-indexing costs a test, under-indexing loses a recipe silently.
     */
    private static void indexRecipes(RecipeManager manager) {
        if (manager == indexedManager) {
            return;
        }
        ITEM_RECIPES.clear();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            for (Item item : acceptedItems(arcane)) {
                ITEM_RECIPES.computeIfAbsent(item, key -> new HashSet<>()).add(holder.id());
            }
        }
        indexedManager = manager;
    }

    private static Set<Item> acceptedItems(IArcaneRecipe recipe) {
        Set<Item> items = new HashSet<>();
        List<Ingredient> ingredients;
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            ingredients = shaped.getIngredients();
        } else if (recipe instanceof ArcaneShapelessCraftingRecipe shapeless) {
            ingredients = shapeless.getIngredients();
        } else {
            return items;
        }
        for (Ingredient ingredient : ingredients) {
            if (ingredient == null || ingredient.isEmpty()) {
                continue;
            }
            for (ItemStack stack : ingredient.getItems()) {
                if (!stack.isEmpty()) {
                    items.add(stack.getItem());
                }
            }
        }
        return items;
    }

    public static boolean satisfiesGrid(IArcaneRecipe recipe, List<ItemStack> cells, @Nullable Level level) {
        if (recipe instanceof ArcaneShapedCraftingRecipe shaped) {
            List<Ingredient> ingredients = shaped.getIngredients();
            int width = shaped.getWidth();
            int height = shaped.getHeight();
            if (width < 1 || height < 1 || width > GRID_SIDE || height > GRID_SIDE) {
                return false;
            }
            if (ingredients.size() < width * height) {
                return false;
            }
            for (int originX = 0; originX + width <= GRID_SIDE; originX++) {
                for (int originY = 0; originY + height <= GRID_SIDE; originY++) {
                    for (boolean mirrored : new boolean[] {false, true}) {
                        if (fitsAt(cells, ingredients, width, height, originX, originY, mirrored)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        }
        if (recipe instanceof ArcaneShapelessCraftingRecipe shapeless) {
            return fitsShapeless(cells, shapeless.getIngredients());
        }
        return false;
    }

    /**
     * One placement of a shaped recipe: ingredients are indexed by the recipe's row width, not the
     * grid's, or a two-wide pattern read three cells at a time compares the wrong columns.
     */
    private static boolean fitsAt(
            List<ItemStack> cells,
            List<Ingredient> ingredients,
            int width,
            int height,
            int originX,
            int originY,
            boolean mirrored) {
        for (int y = 0; y < GRID_SIDE; y++) {
            for (int x = 0; x < GRID_SIDE; x++) {
                ItemStack cell = cells.get(y * GRID_SIDE + x);
                int localX = x - originX;
                int localY = y - originY;
                boolean covered = localX >= 0 && localX < width && localY >= 0 && localY < height;
                if (!covered) {
                    if (!cell.isEmpty()) {
                        return false;
                    }
                    continue;
                }
                int column = mirrored ? width - localX - 1 : localX;
                Ingredient ingredient = ingredients.get(localY * width + column);
                if (ingredient == null || !ingredient.test(cell)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a shapeless recipe's ingredients are present with nothing left over: a full matching, like
     * the workbench's {@code RecipeMatcher}, not a greedy scan (oak must take the "oak planks" slot).
     */
    private static boolean fitsShapeless(List<ItemStack> cells, List<Ingredient> ingredients) {
        List<ItemStack> present = new ArrayList<>(cells.size());
        for (ItemStack cell : cells) {
            if (!cell.isEmpty()) {
                present.add(cell);
            }
        }
        if (present.size() != ingredients.size()) {
            return false;
        }
        return assignable(present, ingredients, new boolean[ingredients.size()], 0);
    }

    private static boolean assignable(
            List<ItemStack> items, List<Ingredient> ingredients, boolean[] used, int index) {
        if (index >= items.size()) {
            return true;
        }
        ItemStack item = items.get(index);
        for (int i = 0; i < ingredients.size(); i++) {
            if (used[i] || !ingredients.get(i).test(item)) {
                continue;
            }
            used[i] = true;
            if (assignable(items, ingredients, used, index + 1)) {
                return true;
            }
            used[i] = false;
        }
        return false;
    }

    /**
     * Resolves a pattern handed in from outside - AE2's encoding terminal or a pattern provider: its
     * entries are concrete stacks, so matching is by membership, not cell by cell.
     * @return the pattern, or {@code null} when no arcane recipe matches both output and inputs
     */
    public static @Nullable ThEArcanePattern resolve(
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
            if (width < 1 || height < 1 || width > GRID_SIDE || height > GRID_SIDE) {
                return null;
            }
            if (optional.size() < width * height) {
                return null;
            }
            // Ingredients come in the recipe's own rows while the grid is three wide: using the recipe's
            // stride would put a two-wide recipe's second row in the grid's first.
            List<ItemStack> cells = new ArrayList<>(GRID_SIDE * GRID_SIDE);
            List<Ingredient> ingredients = new ArrayList<>(width * height);
            for (int row = 0; row < GRID_SIDE; row++) {
                for (int column = 0; column < GRID_SIDE; column++) {
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
            return new Layout(cells, ingredients, Math.min(MAX_GRID, Math.max(1, cells.size())), 1);
        }
        return null;
    }

    /**
     * A representative stack per ingredient: a pattern carries concrete stacks only, so a multi-item
     * ingredient takes its first entry, and a tag the <em>display</em> item - see {@link #cellChoices}.
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

    // ----- Live registry helpers -----

    public AspectList resolveCrystals(HolderLookup.Provider registries) {
        HolderLookup.RegistryLookup<IAspect> lookup =
                registries.lookup(IAspect.REGISTRY_KEY).orElse(null);
        if (lookup == null) {
            return AspectList.EMPTY;
        }
        AspectList resolved = AspectList.EMPTY;
        for (AspectInstance entry : crystals.entries()) {
            Holder<IAspect> holder = lookup.get(entry.aspect().getKey()).orElse(null);
            if (holder == null) {
                ThaumicEnergistics.LOG.debug(
                        "Arcane pattern references unregistered aspect {}",
                        entry.aspect().getKey().location());
                continue;
            }
            resolved = resolved.add(holder, entry.amount());
        }
        return resolved;
    }

    public List<ResourceKey<IAspect>> crystalKeys() {
        return crystals.entries().stream().map(entry -> entry.aspect().getKey()).toList();
    }


    /**
     * Wraps a pattern into the item AE2's CPU saves and decodes: the same {@link #save}/{@link #load}
     * contract as the knowledge core, so a pattern cannot differ between a core and a task list.
     */
    public ItemStack toItem(HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(thaumicenergistics_ce.init.ModItems.ARCANE_PATTERN.get());
        CompoundTag tag = new CompoundTag();
        tag.put("Pattern", save(registries));
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Reads a pattern back out of the item {@link #toItem} produced, or {@code null} if unreadable. */
    public static @Nullable ThEArcanePattern ofItem(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains("Pattern")) {
            return null;
        }
        return load(registries, tag.getCompound("Pattern"));
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Output", result.save(registries));

        ListTag gridTag = new ListTag();
        for (ItemStack stack : grid) {
            gridTag.add(stack.isEmpty() ? new CompoundTag() : stack.save(registries));
        }
        tag.put("Grid", gridTag);
        tag.putInt("GridWidth", gridWidth);
        tag.putInt("GridHeight", gridHeight);

        ListTag tagTag = new ListTag();
        for (int cell = 0; cell < MAX_GRID; cell++) {
            TagKey<Item> cellTag = cellTag(cell);
            tagTag.add(StringTag.valueOf(cellTag == null ? "" : cellTag.location().toString()));
        }
        tag.put("CellTags", tagTag);

        ListTag aspectTag = new ListTag();
        for (AspectInstance entry : crystals.entries()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.putString("Aspect", entry.aspect().getKey().location().toString());
            entryTag.putInt("Amount", entry.amount());
            aspectTag.add(entryTag);
        }
        tag.put("Crystals", aspectTag);

        tag.putInt("BaseVis", baseVis);
        if (research != null) {
            tag.putString("Research", research.toString());
        }
        if (researchStage != null) {
            tag.putInt("ResearchStage", researchStage);
        }
        return tag;
    }

    public static @Nullable ThEArcanePattern load(HolderLookup.Provider registries, CompoundTag tag) {
        if (!tag.contains("Output")) {
            return null;
        }
        ItemStack output = ItemStack.parseOptional(registries, tag.getCompound("Output"));
        if (output.isEmpty()) {
            return null;
        }

        List<ItemStack> grid = new ArrayList<>();
        ListTag gridTag = tag.getList("Grid", Tag.TAG_COMPOUND);
        for (int i = 0; i < gridTag.size(); i++) {
            CompoundTag cell = gridTag.getCompound(i);
            grid.add(cell.isEmpty() ? ItemStack.EMPTY : ItemStack.parseOptional(registries, cell));
        }
        if (grid.isEmpty()) {
            return null;
        }

        AspectList crystals = AspectList.EMPTY;
        HolderLookup.RegistryLookup<IAspect> lookup =
                registries.lookup(IAspect.REGISTRY_KEY).orElse(null);
        if (lookup != null) {
            ListTag aspectTag = tag.getList("Crystals", Tag.TAG_COMPOUND);
            for (int i = 0; i < aspectTag.size(); i++) {
                CompoundTag entryTag = aspectTag.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(entryTag.getString("Aspect"));
                if (id == null) {
                    continue;
                }
                Holder<IAspect> holder = lookup
                        .get(ResourceKey.create(IAspect.REGISTRY_KEY, id))
                        .orElse(null);
                if (holder != null) {
                    crystals = crystals.add(holder, Math.max(1, entryTag.getInt("Amount")));
                }
            }
        }

        ResourceLocation research =
                tag.contains("Research") ? ResourceLocation.tryParse(tag.getString("Research")) : null;
        Integer stage = tag.contains("ResearchStage") ? tag.getInt("ResearchStage") : null;
        int width = tag.contains("GridWidth") ? tag.getInt("GridWidth") : Math.min(MAX_GRID, grid.size());
        int height = tag.contains("GridHeight") ? tag.getInt("GridHeight") : 1;

        List<TagKey<Item>> cellTags = new ArrayList<>(MAX_GRID);
        ListTag tagTag = tag.getList("CellTags", Tag.TAG_STRING);
        for (int cell = 0; cell < MAX_GRID; cell++) {
            ResourceLocation id =
                    cell < tagTag.size() ? ResourceLocation.tryParse(tagTag.getString(cell)) : null;
            cellTags.add(id == null ? null : TagKey.create(Registries.ITEM, id));
        }

        return new ThEArcanePattern(
                output,
                grid,
                // Only the display stacks: the ingredients belong to the recipe, looked up from the manager.
                List.of(),
                // Clamped: the width and height come from a saved pattern, and a million-cell grid is a hang.
                Math.clamp(width, 1, MAX_GRID),
                Math.clamp(height, 1, MAX_GRID),
                crystals,
                tag.getInt("BaseVis"),
                research,
                stage,
                // The tags do survive, the exception above: a tag is not recoverable from the recipe.
                cellTags);
    }
}
