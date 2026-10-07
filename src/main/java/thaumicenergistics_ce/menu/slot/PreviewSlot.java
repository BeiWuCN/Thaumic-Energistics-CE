package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 只显示物品、别无所为的槽位：不能取走、不能放置、悬停也不高亮。
 * 奥术组装机的预览使用它，因为该方块在其更新标签里不携带物品数据，
 * 槽位同步是服务端把物品送到客户端面前的唯一途径。{@code isHighlightable} 也为
 * false，因为亮起的预览槽位看上去像可以往里放东西的地方。
 * 放东西的地方。
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
