package thaumicenergistics_ce.menu.slot;

import java.util.function.BiConsumer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 知识铭刻机合成网格的一个单元，位于玩家所看的那一侧。它是幽灵槽位：记录要编码什么
 * 而不取走物品，因为任务稍后才付费，且物品从不离开玩家，所以这次写入以载荷形式发出
 * 而不是走槽位同步。
 * 允许取走，这样点击某个单元即可将其清空。
 */
public class GhostGridSlot extends Slot {

    private final BiConsumer<Integer, ItemStack> writer;

    public GhostGridSlot(
            Container container, int index, int x, int y, BiConsumer<Integer, ItemStack> writer) {
        super(container, index, x, y);
        this.writer = writer;
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return true;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public void set(ItemStack stack) {
        super.set(stack);
        request(stack);
    }

    @Override
    public void onTake(Player player, ItemStack stack) {
        super.onTake(player, stack);
        // 发空，而不是被取走的物品堆：机器需要的是该单元的新内容，发送被取走
        // 的物品堆等于说该单元仍装着玩家刚移走的配方。
        request(ItemStack.EMPTY);
    }

    private void request(ItemStack stack) {
        writer.accept(getContainerSlot(), stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }
}
