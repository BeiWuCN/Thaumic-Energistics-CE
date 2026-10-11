package thaumicenergistics_ce.menu.slot;

import java.util.function.BiConsumer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

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
        request(ItemStack.EMPTY);
    }

    private void request(ItemStack stack) {
        writer.accept(getContainerSlot(), stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
    }
}
