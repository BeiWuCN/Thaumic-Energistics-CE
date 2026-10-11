package thaumicenergistics_ce.recipe;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

public final class IngredientStacks {
    private IngredientStacks() {}

    public static List<ItemStack> variants(Ingredient ingredient, HolderLookup.Provider registries) {
        if (ingredient.isEmpty()) {
            return List.of();
        }
        List<ItemStack> variants = new ArrayList<>();
        ContextMap context = new ContextMap.Builder()
                .withOptionalParameter(SlotDisplayContext.REGISTRIES, registries)
                .create(SlotDisplayContext.CONTEXT);
        try {
            for (ItemStack stack : ingredient.display().resolveForStacks(context)) {
                if (!stack.isEmpty() && ingredient.test(stack)) {
                    variants.add(stack.copyWithCount(1));
                }
            }
        } catch (RuntimeException ignored) {
            variants.clear();
        }
        if (variants.isEmpty()) {
            ingredient.items().forEach(item -> {
                ItemStack stack = new ItemStack(item.value());
                if (ingredient.test(stack)) {
                    variants.add(stack);
                }
            });
        }
        return variants;
    }
}
