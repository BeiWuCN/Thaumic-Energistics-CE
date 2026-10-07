package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Thaumaturge 的法杖：插在上面的焦点，以及它为一次施法持有的 vis。
 * 法杖类在 {@code content} 包不在 {@code api} 包，会变动；这里按物品类识别，不按标签，依赖保持单向。
 * {@code consumeVis} 的标志位在本类叫 {@link #canPayVis} 和 {@link #payVis}。
 */
public final class TcWand {
    private TcWand() {}

    /** 没插东西的法杖读出空物品堆，不是 null。 */
    public static ItemStack focus(ItemStack wand) {
        return wand.getItem() instanceof ItemWand item ? item.getFocusStack(wand) : ItemStack.EMPTY;
    }

    /** 该物品堆是不是恰好插着 {@code focus} 的法杖，其它情况为 false。 */
    public static boolean holdsFocus(ItemStack wand, Item focus) {
        return focus(wand).is(focus);
    }

    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand;
    }

    /** 空的 {@code focus} 清空插槽；物品堆不是法杖时不动。 */
    public static void setFocus(ItemStack wand, ItemStack focus) {
        if (wand.getItem() instanceof ItemWand item) {
            item.setFocus(wand, focus);
        }
    }

    // -- vis -----------------------------------------------------------------

    /** 只询价，不支付；false 表示法杖付不起。 */
    public static boolean canPayVis(ItemStack wand, Player player, float amount) {
        return wand.getItem() instanceof ItemWand item
                && item.consumeVis(wand, player, amount, false, true);
    }

    /** 支付 vis；付不起时为 false。 */
    public static boolean payVis(ItemStack wand, Player player, float amount) {
        return wand.getItem() instanceof ItemWand item
                && item.consumeVis(wand, player, amount, false, false);
    }
}
