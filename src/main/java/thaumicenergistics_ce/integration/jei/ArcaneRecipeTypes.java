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
        if (arcane instanceof ArcaneShapedCraftingRecipe shaped) {
            // 样板的 3x3 布局：直接读 ingredients 会把两格宽的行错位，283 个里错 20 个。
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
                // 那格里原料的每一种变体，转移好挑一个玩家有的。
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
}
