package thaumicenergistics_ce.integration.jei;

import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.util.context.ContextMap;
import net.minecraft.core.Holder;
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
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.integration.ae2.ClientRegistries;
import thaumicenergistics_ce.util.ThELog;

/**
 * 怎么读 Thaumaturge 奥术配方的网格：一次转移会填的九个格子，以及放不放得下。
 * 它不点 JEI 类型，专用服务端没有 JEI 可借；那是 [ArcaneJeiRecipeType] 的事。
 */
public final class ArcaneRecipeTypes {

    private ArcaneRecipeTypes() {}

    /**
     * 配方要的九个网格格，按阅读顺序，给成变体列表；空列表就是空格。
     * 从样板读：每格一个物品堆，不是完整原料。
     */
    public static @Nullable List<List<ItemStack>> cellsFor(RecipeHolder<?> holder) {
        if (!(holder.value() instanceof IArcaneRecipe arcane)) {
            return null;
        }
        if (arcane instanceof ArcaneShapedCraftingRecipe) {
            // 样板的 3x3 布局：直接读 ingredients 会把两格宽的行错位，283 个里错 20 个。
            ItemStack output = outputOf(arcane);
            if (output.isEmpty()) {
                return null;
            }
            ThEArcanePattern pattern = ThEArcanePattern.fromRecipe(arcane, output);
            if (pattern == null) {
                return null;
            }
            // 按网格对齐，用的是样板自己那个读取器：原始材料列表按配方自己的行排，
            // 拿格位 N 配原始材料 N，每个两格宽的行都会错位。
            List<Optional<Ingredient>> ingredients = ThEArcanePattern.gridAlignedIngredients(arcane);
            List<ItemStack> grid = pattern.grid();
            List<List<ItemStack>> cells = new ArrayList<>(ThEArcanePattern.MAX_GRID);
            for (int i = 0; i < ThEArcanePattern.MAX_GRID; i++) {
                ItemStack representative = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
                if (representative.isEmpty()) {
                    cells.add(List.of());
                    continue;
                }
                // 那格里材料的每一种变体，转移好挑一个玩家有的。
                // 走材料的 display 读，不走它的物品列表：自定义材料把让它与众不同的地方
                // ——组件过滤器的补丁——只留在 display 里。它的物品列表是光秃秃的物品，
                // 而光秃秃的 thaumaturge:essentia_crystal 不带 crystal_aspect，照它建出的
                // 样板拿到的物品堆连配方自己的网格都不认：每个带组件过滤的格位都回 "no match"。
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

    /**
     * 材料能被哪些物品堆满足，按转移会交出去的样子。来源是 display，
     * 不是 {@link Ingredient#items()}：普通材料两者是同一份物品列表，
     * 自定义材料（NeoForge 的组件过滤器）把让它与众不同的补丁留在 display 里，
     * 物品列表则光秃秃——在那里读列表，交给配方的物品堆会被拒。
     */
    private static List<ItemStack> displayVariants(Optional<Ingredient> ingredient) {
        if (ingredient.isEmpty() || ingredient.get().isEmpty()) {
            return List.of();
        }
        List<ItemStack> variants = new ArrayList<>();
        try {
            ContextMap context = new ContextMap.Builder()
                    .withOptionalParameter(SlotDisplayContext.REGISTRIES, registryAccess())
                    .create(SlotDisplayContext.CONTEXT);
            for (ItemStack stack : ingredient.get().display().resolveForStacks(context)) {
                if (!stack.isEmpty()) {
                    variants.add(stack.copyWithCount(1));
                }
            }
        } catch (RuntimeException e) {
            // display 要的不止注册表时——材料的 display 不是物品堆形状——落回下面的普通物品列表，
            // 这里本来就是它以前读的地方。
            variants.clear();
        }
        if (variants.isEmpty()) {
            ingredient.get().items().forEach(item -> variants.add(new ItemStack(item.value())));
        }
        return variants;
    }

    /**
     * 这配方放不放得进机器的网格：有序配方按自己的形状补齐，总能进 3x3；
     * 无序配方要它的原料进得了九格。
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
     * 转移会填的网格，纯物品堆形式，给自测用：每格取第一个变体，
     * 也就是转移自己的兜底。这里对不上说明匹配器不工作。
     */
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

    /**
     * 为配方结果取注册表访问：有服务端用服务端的，否则经客户端 sink 用本侧的。
     * 绝不用 Thaumaturge 的 JEI 插件，专用服务端加载不了。
     */
    private static HolderLookup.Provider registryAccess() {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return ClientRegistries.get();
    }

    /**
     * 配方产出的物品堆：{@code Recipe} 已经没有结果访问器了，结果由 {@link RecipeDisplay} 承载。
     * 只填了注册表查询，标签支撑的结果才会经它解析出来，而不是空手而归。
     */
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
