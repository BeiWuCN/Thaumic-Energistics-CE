package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Thaumaturge 的法杖：插在其上的焦点，以及它为一次施法持有的 vis。法杖位于
 * 其 {@code content} 包而不是 {@code api} 包中，所以它会变动；它按物品类
 * 识别而不是按标签识别，从而让依赖保持单向。
 * {@code consumeVis} 的标志位在我们的调用处名为 {@link #canPayVis} 和 {@link #payVis}。
 */
public final class TcWand {
    private TcWand() {}

    /** 没有插任何东西的法杖读出来是空物品堆，绝不会是 null。 */
    public static ItemStack focus(ItemStack wand) {
        return wand.getItem() instanceof ItemWand item ? item.getFocusStack(wand) : ItemStack.EMPTY;
    }

    /** 该物品堆是否为恰好携带 {@code focus} 的法杖；其它情况为 false。 */
    public static boolean holdsFocus(ItemStack wand, Item focus) {
        return focus(wand).is(focus);
    }

    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand;
    }

    /** 空的 {@code focus} 会清空插槽。该物品堆不是法杖时什么都不做。 */
    public static void setFocus(ItemStack wand, ItemStack focus) {
        if (wand.getItem() instanceof ItemWand item) {
            item.setFocus(wand, focus);
        }
    }

    // -- vis -----------------------------------------------------------------

    /** 只询价而不支付：false 表示法杖付不起这次施法。 */
    public static boolean canPayVis(ItemStack wand, Player player, float amount) {
        return wand.getItem() instanceof ItemWand item
                && item.consumeVis(wand, player, amount, false, true);
    }

    /** 支付的代价：法杖付不起时为 false。 */
    public static boolean payVis(ItemStack wand, Player player, float amount) {
        return wand.getItem() instanceof ItemWand item
                && item.consumeVis(wand, player, amount, false, false);
    }
}
