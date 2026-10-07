package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 蒸馏编码器的源槽位：指名要蒸馏何物的模板槽位。物品从不交付——由任务来支付它——
 * 所以放置、取走与读取用的是同一个物品堆。这是一项 JEI 拖拽防护措施：普通槽位会
 * 免费交出被拖入的物品，造成复制；与只读显示不同，
 * 它仍然同步，因为 {@code set} 未被触碰。
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
