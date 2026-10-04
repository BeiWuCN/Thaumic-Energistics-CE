package thaumicenergistics_ce.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Cheap change-detection keys for stacks and grids.
 * <ul>
 *   <li>Each replaced a string from {@code getComponentsPatch()}, which serialises every component
 *       to SNBT - a once-a-frame path re-serialised sixty times a second.
 *   <li>An int hash can collide, but a collision costs one skipped recomputation, not a wrong answer.
 * </ul>
 */
public final class StackSignatures {

    private StackSignatures() {}

    /** {@code 0} for an empty stack; otherwise the item, the count, and a hash of the components. */
    public static int of(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return 31 * (31 * BuiltInRegistries.ITEM.getId(stack.getItem()) + stack.getCount())
                + stack.getComponentsPatch().hashCode();
    }

    /**
     * A running key over a sequence of stacks. Order matters, so this is not a sum: two grids holding the
     * same items in different cells stand for different recipes and must not hash equal.
     */
    public static int of(Iterable<ItemStack> stacks) {
        int hash = 1;
        for (ItemStack stack : stacks) {
            hash = 31 * hash + of(stack);
        }
        return hash;
    }
}
