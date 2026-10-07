package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 机器写入、玩家取空的槽位：蒸馏编码器写出的样板。它拒绝放置并允许取走，
 * 因为只有 {@code encode()} 会写这个样板。它不是 {@code ReadOnlySlot}：空操作的
 * {@code set} 会吞掉客户端的 {@code AbstractContainerMenu.setItem} 写入，
 * 于是样板永远到不了屏幕。
 */
public class MachineOutputSlot extends Slot {

    public MachineOutputSlot(Container container, int slot, int x, int y) {
        super(container, slot, x, y);
    }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }
}
