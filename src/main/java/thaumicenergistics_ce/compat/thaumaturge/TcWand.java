package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Thaumaturge's wand: the focus socketed in it, and the vis it holds for a cast.
 *
 * <ul>
 *   <li>The wand lives in its {@code content} package rather than its {@code api} one, so it moves.
 *   <li>A wand is recognised by item class rather than by tag, which is what keeps this mod's
 *       dependency on Thaumaturge one-way.
 *   <li>{@code consumeVis} takes a crafting flag and a simulate flag. Every call this mod makes is a
 *       non-crafting one, so the two readings are named {@link #canPayVis} and {@link #payVis} rather
 *       than left as two bare booleans at the call site.
 * </ul>
 */
public final class TcWand {
    private TcWand() {}

    /** A wand with nothing socketed reads as an empty stack, never as null. */
    public static ItemStack focus(ItemStack wand) {
        return wand.getItem() instanceof ItemWand item ? item.getFocusStack(wand) : ItemStack.EMPTY;
    }

    /** Whether this stack is a wand carrying exactly {@code focus}; false for anything else. */
    public static boolean holdsFocus(ItemStack wand, Item focus) {
        return focus(wand).is(focus);
    }

    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand;
    }

    /** An empty {@code focus} clears the socket. Does nothing when the stack is not a wand. */
    public static void setFocus(ItemStack wand, ItemStack focus) {
        if (wand.getItem() instanceof ItemWand item) {
            item.setFocus(wand, focus);
        }
    }

    // -- vis -----------------------------------------------------------------

    /** The price asked without paying it: false means the wand cannot afford the cast. */
    public static boolean canPayVis(ItemStack wand, Player player, float amount) {
        return wand.getItem() instanceof ItemWand item
                && item.consumeVis(wand, player, amount, false, true);
    }

    /** Pays the price: false when the wand cannot afford it. */
    public static boolean payVis(ItemStack wand, Player player, float amount) {
        return wand.getItem() instanceof ItemWand item
                && item.consumeVis(wand, player, amount, false, false);
    }
}
