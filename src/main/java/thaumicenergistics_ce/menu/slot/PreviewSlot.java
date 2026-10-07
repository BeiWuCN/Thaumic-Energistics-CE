package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 只显示物品、别的什么都不做的槽位：不能取走、不能放置、悬停也不高亮。
 * 奥术组装机的预览用它：该方块不在更新标签里带物品数据，槽位同步是服务端把物品送到客户端的唯一途径。
 * {@code isHighlightable} 也是 false，亮起来的预览槽位看着像能往里放东西。
 */
public class PreviewSlot extends Slot {

    public PreviewSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
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
}
