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
 * 一个解析完成的 Thaumaturge 奥术合成任务：网格布局、vis 价格、元初晶体。它
 * 由实时 {@link RecipeManager} 推导而来，因此数据包改动在下次重载时生效，且只有
 * {@link #save}/{@link #load} 能把它与知识核心互相往返。
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

    /** 每消耗一个元初晶体对应的 vis，与 Thaumaturge 自身的法杖替代比率一致。 */
    public static final int CRYSTAL_SUBSTITUTE_VIS = 2;

    /** 用环境 vis 代替法杖支付时的附加费，与 Thaumaturge 保持一致。 */
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
     * 网格格位所代表的标签；普通物品则为 {@code null}：之所以携带它，是因为来自
     * 核心的样板背后没有配方——见 {@link #load}。
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
     * 以物品形式提供的晶体：即非元初的那些。复合晶体按其元初要素计入，
     * Thaumaturge 不会对它做替代。
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

    // ----- 对照实时配方管理器解析 -----

    /**
     * 把产出 {@code result} 的奥术配方转换为样板；若没有任何配方产出该
     * 精确物品堆，则返回 {@code null}。
     */
    public static @Nullable ThEArcanePattern fromResult(@Nullable Level level, ItemStack result) {
        return ArcanePatternLookup.fromResult(level, result);
    }

    /**
     * 用玩家编码的 AE2 样板所声称编码的奥术配方来校验该样板。
     * @return 样板；没有奥术配方匹配时返回 {@code null}
     */
    public static @Nullable ThEArcanePattern fromEncoded(
            @Nullable Level level, List<ItemStack> patternInputs, ItemStack output) {
        return ArcanePatternLookup.fromEncoded(level, patternInputs, output);
    }

    /**
     * {@code inputs} 中每个非空条目是否都能作为网格格位或晶体被消耗：不检查
     * 数量，合成前会用实时配方重新匹配。
     */
    public boolean acceptsInputs(List<ItemStack> inputs) {
        return ArcaneGridMatcher.acceptsInputs(grid, cellTags, crystalItems(), inputs);
    }

    public static @Nullable ThEArcanePattern fromRecipe(IArcaneRecipe recipe, ItemStack output) {
        return ArcanePatternLookup.fromRecipe(recipe, output);
    }

    /**
     * 找出手工填好的 3x3 网格所代表的奥术配方。
     * @return 样板；没有奥术配方匹配该网格时返回 {@code null}
     */
    public static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        return ArcanePatternLookup.resolveGrid(level, cells);
    }

    public static boolean satisfiesGrid(IArcaneRecipe recipe, List<ItemStack> cells, @Nullable Level level) {
        return ArcaneGridMatcher.satisfiesGrid(recipe, cells, level);
    }

    /**
     * 解析外部传入的样板——AE2 的编码终端或样板供应器：它的条目是具体物品堆，
     * 所以按成员关系匹配，而不是逐格匹配。
     * @return 样板；没有奥术配方同时匹配产物与输入时返回 {@code null}
     */
    public static @Nullable ThEArcanePattern resolve(
            @Nullable Level level, List<ItemStack> inputs, ItemStack output) {
        return ArcanePatternLookup.resolve(level, inputs, output);
    }

    // ----- 实时注册表辅助方法 -----

    public AspectList resolveCrystals(HolderLookup.Provider registries) {
        return ArcaneResearchGate.resolveCrystals(crystals, registries);
    }

    public List<ResourceKey<IAspect>> crystalKeys() {
        return ArcaneResearchGate.crystalKeys(crystals);
    }

    /**
     * 把样板包装成 AE2 的合成 CPU 保存与解码所用的物品：与知识核心相同的
     * {@link #save}/{@link #load} 约定，因此样板在核心与任务列表之间不会有差异。
     */
    public ItemStack toItem(HolderLookup.Provider registries) {
        return ArcanePatternTags.toItem(this, registries);
    }

    /** 从 {@link #toItem} 产出的物品中读回样板，无法读取时返回 {@code null}。 */
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
