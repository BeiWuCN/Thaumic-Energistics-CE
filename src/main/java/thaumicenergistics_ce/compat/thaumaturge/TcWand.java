package thaumicenergistics_ce.compat.thaumaturge;

import com.leclowndu93150.thaumaturge.content.wands.ItemWand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.content.wands.WandVisHelper;
import java.util.Map;
import net.minecraft.resources.ResourceKey;

public final class TcWand {
    private TcWand() {}

    public static ItemStack focus(ItemStack wand) {
        return wand.getItem() instanceof ItemWand item ? item.getFocusStack(wand) : ItemStack.EMPTY;
    }

    public static boolean holdsFocus(ItemStack wand, Item focus) {
        return focus(wand).is(focus);
    }

    public static boolean isWand(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ItemWand;
    }

    public static boolean canPayExact(ItemStack wand, Map<ResourceKey<IAspect>, Integer> cost) {
        return cost.isEmpty() || isWand(wand) && WandVisHelper.payExact(wand, cost, true);
    }

    public static void setFocus(ItemStack wand, ItemStack focus) {
        if (wand.getItem() instanceof ItemWand item) {
            item.setFocus(wand, focus);
        }
    }


    public static boolean canPayVis(ItemStack wand, Player player, float amount) {
        return wand.getItem() instanceof ItemWand item
                && item.consumeVis(wand, player, amount, false, true);
    }

    public static boolean payVis(ItemStack wand, Player player, float amount) {
        return wand.getItem() instanceof ItemWand item
                && item.consumeVis(wand, player, amount, false, false);
    }
}
