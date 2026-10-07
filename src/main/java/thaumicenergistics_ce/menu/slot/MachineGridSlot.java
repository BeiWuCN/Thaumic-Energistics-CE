package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 知识铭刻机合成网格的服务端那一半。
 * 与 {@link GhostGridSlot} 形状相同且没有载荷：服务端已持有机器所读的网格，两侧的槽位数量要一致。
 * 拒绝取走：在这里点击会把玩家从未放入的材料抽出来。
 */
public class MachineGridSlot extends Slot {

    public MachineGridSlot(Container container, int index, int x, int y) {
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
    public int getMaxStackSize() {
        return 1;
    }
}
