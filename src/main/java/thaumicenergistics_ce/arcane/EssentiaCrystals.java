package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.registry.TCDataComponents;
import com.leclowndu93150.thaumaturge.registry.TCItems;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * Bridge to Thaumaturge's essentia crystals: arcane recipes name an aspect rather than an item, so the
 * encoder and the assembler both map aspect to crystal stack here.
 */
public final class EssentiaCrystals {
    private EssentiaCrystals() {}

    /** Whether {@code stack} is an essentia crystal of any configuration. */
    public static boolean isCrystal(ItemStack stack) {
        return TcRegistry.isCrystal(stack);
    }

    /** The aspect a configured crystal carries, or {@code null} for a plain or malformed crystal. */
    public static @Nullable Holder<IAspect> aspectOf(ItemStack stack) {
        if (!isCrystal(stack)) {
            return null;
        }
        return TcRegistry.crystalAspect(stack);
    }

    /**
     * Creates a stack of {@code count} crystals configured for {@code aspect}, writing the same data
     * component Thaumaturge's crystal factory does so the result is interchangeable with workbench or
     * crucible crystals.
     */
    public static ItemStack create(Holder<IAspect> aspect, int count) {
        ItemStack stack = new ItemStack(TCItems.ESSENTIA_CRYSTAL.get(), Math.max(1, count));
        stack.set(TCDataComponents.CRYSTAL_ASPECT.get(), new AspectInstance(aspect, 1));
        return stack;
    }

    /** Number of crystals carrying {@code aspect} among the supplied stacks. */
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
