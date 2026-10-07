package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 知识铭刻机合成网格的服务端那一半。它与 {@link GhostGridSlot} 形状相同
 * 且没有载荷，因为服务端已持有机器所读的网格，两侧必须布置相同的槽位数量。
 * 拒绝取走，因为在这里
 * 点击会让玩家抽出他们从未放入的材料。
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
