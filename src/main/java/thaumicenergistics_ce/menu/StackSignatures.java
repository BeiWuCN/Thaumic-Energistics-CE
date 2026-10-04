package thaumicenergistics_ce.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Cheap change-detection keys for stacks and grids.
 *
 * <ul>
 * <li>Each replaced a string built by appending {@code getComponentsPatch()}, whose {@code toString}
 * serialises every component to SNBT - for a knowledge core those components are the stored pattern
 * list, so a once-a-frame path re-serialised it sixty times a second.</li>
 * <li>An int hash walks the same components without allocating or formatting. It can collide, but every
 * caller only decides whether to <em>recompute</em>, and a collision costs one skipped recomputation
 * rather than a wrong answer, because the recomputation itself reads the real stacks.</li>
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
