package thaumicenergistics_ce.menu;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Cheap change-detection keys for stacks and grids.
 *
 * <p>Every one of these replaced a string built by appending {@code getComponentsPatch()}, and that turned
 * out to be the most expensive thing several menus did. Appending a component patch calls its
 * {@code toString}, which serialises every component to SNBT - and for a knowledge core the components
 * <em>are</em> the stored pattern list. On paths that run once a frame that meant re-serialising a whole
 * recipe store sixty times a second, which is how it was reported:
 *
 * <pre>"它会循环知识核心中所储存的所有配方以每帧的形式"</pre>
 *
 * <p>An int hash walks the same components but allocates nothing and formats nothing. It is not a perfect
 * fingerprint - two different component sets could collide - but every caller uses it to decide whether to
 * <em>recompute</em>, and a collision costs one skipped recomputation rather than a wrong answer, because
 * the recomputation itself reads the real stacks.
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
     * A running key over a sequence of stacks.
     *
     * <p>Order matters, so this is not a sum: two grids holding the same items in different cells stand for
     * different recipes, and a commutative combination would call them equal.
     */
    public static int of(Iterable<ItemStack> stacks) {
        int hash = 1;
        for (ItemStack stack : stacks) {
            hash = 31 * hash + of(stack);
        }
        return hash;
    }
}
