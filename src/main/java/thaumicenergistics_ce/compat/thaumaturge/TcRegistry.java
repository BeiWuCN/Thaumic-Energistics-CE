package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.essentia.jar.BlockEntityJar;
import com.leclowndu93150.thaumaturge.content.essentia.jar.JarItem;
import com.leclowndu93150.thaumaturge.content.item.PhialItem;
import com.leclowndu93150.thaumaturge.content.taint.item.EssentiaCrystalFactory;
import com.leclowndu93150.thaumaturge.content.taint.item.ItemEssentiaCrystal;
import com.leclowndu93150.thaumaturge.registry.TCDataComponents;
import com.leclowndu93150.thaumaturge.registry.TCItems;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Thaumaturge's item registry, and the stacks this mod builds out of it.
 *
 * <ul>
 *   <li>{@code TCItems.ALCHEMICAL_FURNACE} was deleted outright in 0.4.7 - registry entries move.
 *   <li>Nothing outside this package names a {@code TCItems} field or a {@code TCDataComponents} one.
 * </ul>
 */
public final class TcRegistry {
    private TcRegistry() {}

    // -- essentia crystals ---------------------------------------------------

    public static boolean isCrystal(ItemStack stack) {
        return !stack.isEmpty() && stack.is(TCItems.ESSENTIA_CRYSTAL.get());
    }

    /** The aspect a configured crystal carries, or null for an empty or unconfigured crystal. */
    public static @Nullable Holder<IAspect> crystalAspect(ItemStack stack) {
        return isCrystal(stack) ? ItemEssentiaCrystal.aspectOf(stack) : null;
    }

    public static ItemStack crystalStack(Holder<IAspect> aspect, int count) {
        ItemStack stack = new ItemStack(TCItems.ESSENTIA_CRYSTAL.get(), Math.max(1, count));
        stack.set(TCDataComponents.CRYSTAL_ASPECT.get(), new AspectInstance(aspect, 1));
        return stack;
    }

    public static ItemStack crystalFor(Holder<IAspect> aspect, int amount) {
        return EssentiaCrystalFactory.of(aspect, amount);
    }

    // -- essentia containers -------------------------------------------------

    /** The research book, recognised by item rather than by class. */
    public static boolean isThaumonomicon(ItemStack stack) {
        return stack.is(TCItems.THAUMONOMICON.get());
    }

    /** Whether {@code stack} is a jar or a phial, the two container kinds this mod fills. */
    public static boolean isEssentiaContainer(ItemStack stack) {
        return !stack.isEmpty()
                && (stack.getItem() instanceof JarItem || stack.getItem() instanceof PhialItem);
    }

    public static boolean isPhial(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof PhialItem;
    }

    public static int jarCapacity() {
        return BlockEntityJar.CAPACITY;
    }

    public static int phialCapacity() {
        return PhialItem.BASE_AMOUNT;
    }

    public static ItemStack filledPhial(Holder<IAspect> aspect, int amount) {
        return PhialItem.makeFilled(aspect, amount);
    }

    /** A stack of empty phials: an emptied one is spent back into its own item id, not left behind. */
    public static ItemStack emptyPhials(int count) {
        return new ItemStack(TCItems.PHIAL.get(), count);
    }
}
