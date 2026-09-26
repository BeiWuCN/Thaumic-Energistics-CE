package thaumicenergistics.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.taint.item.ItemEssentiaCrystal;
import com.leclowndu93150.thaumaturge.registry.TCDataComponents;
import com.leclowndu93150.thaumaturge.registry.TCItems;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Bridge to Thaumaturge's essentia crystals.
 *
 * <p>Arcane recipes express their crystal requirement as an aspect list rather than as concrete items,
 * so both the pattern encoder and the assembler need to map aspect to crystal stack and back. All of
 * that knowledge is centralised here.
 */
public final class EssentiaCrystals {
    private EssentiaCrystals() {}

    /** Whether {@code stack} is an essentia crystal of any configuration. */
    public static boolean isCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TCItems.ESSENTIA_CRYSTAL.get());
    }

    /** The aspect a configured crystal carries, or {@code null} for a plain or malformed crystal. */
    public static @Nullable Holder<IAspect> aspectOf(ItemStack stack) {
        if (!isCrystal(stack)) {
            return null;
        }
        return ItemEssentiaCrystal.aspectOf(stack);
    }

    /**
     * Creates a stack of {@code count} crystals configured for {@code aspect}.
     *
     * <p>Writes the same data component Thaumaturge's own crystal factory does, so the result is
     * interchangeable with crystals produced by the workbench or the crucible.
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
