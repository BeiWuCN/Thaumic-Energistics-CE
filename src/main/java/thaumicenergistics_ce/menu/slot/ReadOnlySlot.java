package thaumicenergistics_ce.menu.slot;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 只用于显示的槽位：渲染真实的物品堆，但拒绝一切交互。它展示玩家不得取走、
 * 插入或被模组排序的真实机器状态。一个空的零槽位容器会对自动化隐藏
 * {@code slot.index} 与 {@code slot.container}。每个会改动状态的入口点都是空操作，
 * 而 {@link #getItem()} 读取真实来源。
 */
public class ReadOnlySlot extends Slot {

    private static final Container PLACEHOLDER = new SimpleContainer(0);

    private final Container source;
    private final int sourceIndex;

    public ReadOnlySlot(Container source, int sourceIndex, int x, int y) {
        super(PLACEHOLDER, 0, x, y);
        this.source = source;
        this.sourceIndex = sourceIndex;
    }

    @Override
    public ItemStack getItem() {
        return source.getItem(sourceIndex);
    }

    @Override
    public boolean hasItem() {
        return !getItem().isEmpty();
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
    public void set(ItemStack stack) {
    }

    @Override
    public void setChanged() {
    }

    @Override
    public int getMaxStackSize() {
        return 0;
    }

    @Override
    public ItemStack remove(int amount) {
        return ItemStack.EMPTY;
    }
}
