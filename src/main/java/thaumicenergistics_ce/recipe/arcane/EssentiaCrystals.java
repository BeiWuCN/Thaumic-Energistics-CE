package thaumicenergistics_ce.recipe.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

public final class EssentiaCrystals {
    private EssentiaCrystals() {}

    public static boolean isCrystal(ItemStack stack) {
        return TcRegistry.isCrystal(stack);
    }

    public static @Nullable Holder<IAspect> aspectOf(ItemStack stack) {
        if (!isCrystal(stack)) {
            return null;
        }
        return TcRegistry.crystalAspect(stack);
    }

    public static ItemStack create(Holder<IAspect> aspect, int count) {
        return TcRegistry.crystalStack(aspect, count);
    }

    public static int countOf(Iterable<ItemStack> stacks, Holder<IAspect> aspect) {
        int total = 0;
        for (ItemStack stack : stacks) {
            Holder<IAspect> carried = aspectOf(stack);
            if (carried != null && carried.equals(aspect)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
