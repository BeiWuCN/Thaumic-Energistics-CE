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
 * 一份解析好的 Thaumaturge 奥术合成任务：网格布局、vis 价格、元初晶体。
 * 数据从实时 {@link RecipeManager} 推导，数据包改动下次重载生效；
 * 只有 {@link #save}/{@link #load} 能在它和知识核心之间往返。
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

    /** 消耗一个元初晶体得到的 vis，取自 Thaumaturge 的法杖替代比率。 */
    public static final int CRYSTAL_SUBSTITUTE_VIS = 2;

    /** 拿环境 vis 顶法杖支付要多收 1.25 倍，和 Thaumaturge 一致。 */
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
     * 这一格代表的标签；普通物品是 {@code null}。
     * 从知识核心来的样板背后没有配方，标签就得带上，见 {@link #load}。
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
     * 以物品形式提供的晶体，也就是非元初的那些。
     * 复合晶体按元初要素计入，Thaumaturge 对它不做替代。
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

    // 对照实时配方管理器解析

    /**
     * 把产出 {@code result} 的奥术配方转成样板；
     * 没有配方产出该精确物品堆时返回 {@code null}。
     */
    public static @Nullable ThEArcanePattern fromResult(@Nullable Level level, ItemStack result) {
        return ArcanePatternLookup.fromResult(level, result);
    }

    /**
     * 按 AE2 样板自己声称的奥术配方来校验该样板。
     * @return 样板；没有奥术配方匹配时返回 {@code null}
     */
    public static @Nullable ThEArcanePattern fromEncoded(
            @Nullable Level level, List<ItemStack> patternInputs, ItemStack output) {
        return ArcanePatternLookup.fromEncoded(level, patternInputs, output);
    }

    /**
     * {@code inputs} 里每个非空条目能不能当网格格位或晶体消耗。
     * 不查数量：合成前会用实时配方重新匹配。
     */
    public boolean acceptsInputs(List<ItemStack> inputs) {
        return ArcaneGridMatcher.acceptsInputs(grid, cellTags, crystalItems(), inputs);
    }

    public static @Nullable ThEArcanePattern fromRecipe(IArcaneRecipe recipe, ItemStack output) {
        return ArcanePatternLookup.fromRecipe(recipe, output);
    }

    /**
     * 找出这个手工填好的 3x3 网格代表的奥术配方。
     * @return 样板；没有奥术配方匹配该网格时返回 {@code null}
     */
    public static @Nullable ThEArcanePattern resolveGrid(@Nullable Level level, List<ItemStack> cells) {
        return ArcanePatternLookup.resolveGrid(level, cells);
    }

    public static boolean satisfiesGrid(IArcaneRecipe recipe, List<ItemStack> cells, @Nullable Level level) {
        return ArcaneGridMatcher.satisfiesGrid(recipe, cells, level);
    }

    /**
     * 解析外部来的样板：AE2 编码终端或样板供应器。
     * 它的条目是具体物品堆，按成员关系匹配，不逐格匹配。
     * @return 样板；没有奥术配方同时匹配产物与输入时返回 {@code null}
     */
    public static @Nullable ThEArcanePattern resolve(
            @Nullable Level level, List<ItemStack> inputs, ItemStack output) {
        return ArcanePatternLookup.resolve(level, inputs, output);
    }

    // 实时注册表辅助方法

    public AspectList resolveCrystals(HolderLookup.Provider registries) {
        return ArcaneResearchGate.resolveCrystals(crystals, registries);
    }

    public List<ResourceKey<IAspect>> crystalKeys() {
        return ArcaneResearchGate.crystalKeys(crystals);
    }

    /**
     * 把样板包成 AE2 合成 CPU 保存与解码的物品。
     * 与知识核心同一套 {@link #save}/{@link #load} 约定，样板在核心和任务列表之间不会有差异。
     */
    public ItemStack toItem(HolderLookup.Provider registries) {
        return ArcanePatternTags.toItem(this, registries);
    }

    /** 从 {@link #toItem} 出来的物品读回样板，读不出来返回 {@code null}。 */
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
