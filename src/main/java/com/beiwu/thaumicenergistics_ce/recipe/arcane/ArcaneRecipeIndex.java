package com.beiwu.thaumicenergistics_ce.recipe.arcane;

import com.leclowndu93150.thaumaturge.api.recipe.IArcaneRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapedCraftingRecipe;
import com.leclowndu93150.thaumaturge.content.recipe.workbench.ArcaneShapelessCraftingRecipe;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.jspecify.annotations.Nullable;

/**
 * 记哪些奥术配方收哪些物品，手工填的网格在匹配前先缩候选。
 *
 * <p>索引按 {@link RecipeManager} 实例分开存：单人游戏里客户端和服务端各有一份
 * 配方管理器，共用一个静态索引会互相顶掉缓存——每调用一次就重建整张表，
 * 而且边重建边被另一个线程遍历，直接抛 {@code ConcurrentModificationException}。
 * 缓存用弱键，整合服务器关闭时管理器被回收，索引跟着走。
 *
 * <p>建好的索引是只读的（每个 {@code Set} 都 {@code Set.copyOf}），
 * 取用侧拿到之后不会再被谁改。
 */
final class ArcaneRecipeIndex {

    private static final Map<RecipeManager, Map<Item, Set<ResourceLocation>>> CACHE =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ArcaneRecipeIndex() {}

    /**
     * 收得下这件物品的配方 id；索引里没有这件物品时返回 {@code null}，调用方回落到全量扫描。
     */
    static @Nullable Set<ResourceLocation> accepting(RecipeManager manager, Item item) {
        Map<Item, Set<ResourceLocation>> index;
        synchronized (CACHE) {
            index = CACHE.computeIfAbsent(manager, ArcaneRecipeIndex::build);
        }
        return index.get(item);
    }

    /**
     * 把物品登记到所有收它的材料下：多登记只多一次检测，少登记会无声丢配方。
     */
    private static Map<Item, Set<ResourceLocation>> build(RecipeManager manager) {
        Map<Item, Set<ResourceLocation>> mutable = new HashMap<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            if (!(holder.value() instanceof IArcaneRecipe arcane)) {
                continue;
            }
            for (Item item : acceptedItems(arcane)) {
                mutable.computeIfAbsent(item, key -> new HashSet<>()).add(holder.id());
            }
        }
        Map<Item, Set<ResourceLocation>> frozen = new HashMap<>(mutable.size());
        mutable.forEach((item, ids) -> frozen.put(item, Set.copyOf(ids)));
        return frozen;
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
}
