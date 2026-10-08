package thaumicenergistics_ce.menu.slot;

import java.util.function.IntSupplier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 蒸馏编码器要素行中的一个槽位，不放也不取：一次点击意思是「用这一个」，菜单先截获它。
 * 槽位索引就是该要素在行中的位置，{@code i} 对应源物品提供的第 i 个要素，{@code -1} 是已选要素的显示。
 */
public class AspectSelectSlot extends Slot {

    private final int aspectIndex;

    // 行的大小与已选要素以读取的形式传入：指名自己菜单的槽位正是循环依赖的一半。
    private final IntSupplier aspectCount;

    private final IntSupplier selection;

    public AspectSelectSlot(
            Container container,
            int containerSlot,
            int x,
            int y,
            int aspectIndex,
            IntSupplier aspectCount,
            IntSupplier selection) {
        super(container, containerSlot, x, y);
        this.aspectIndex = aspectIndex;
        this.aspectCount = aspectCount;
        this.selection = selection;
    }

    public int aspectIndex() {
        return aspectIndex;
    }

    public boolean isFilled() {
        return aspectIndex >= 0 && aspectIndex < aspectCount.getAsInt();
    }

    public boolean isSelected() {
        return aspectIndex >= 0 && selection.getAsInt() == aspectIndex;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    @Override
    public boolean mayPickup(Player player) {
        return false;
    }

    @Override
    public boolean isHighlightable() {
        return false;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
