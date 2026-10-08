package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 蒸馏编码器的源井：指名要蒸馏何物的模板槽。物品从不交出去，由任务付账，
 * 故放、取、读用同一个物品堆。这是 JEI 拖拽防护：普通槽会把拖入的物品白送一份，造成复制；
 * 和只读显示不同，它仍然同步，{@code set} 没被动过。
 */
public class TemplateSlot extends Slot {

    public TemplateSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
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
