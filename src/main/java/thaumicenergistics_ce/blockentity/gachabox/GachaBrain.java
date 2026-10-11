package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import thaumicenergistics_ce.block.BlockGachaBox;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

final class GachaBrain {

    private static final String TAG_BRAIN = "Brain";

    private final BlockEntityGachaBox box;

    private final SimpleContainer container = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            box.setChanged();
            GachaBrain.this.settled();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return TcRegistry.isJarBrain(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    };

    GachaBrain(BlockEntityGachaBox box) {
        this.box = box;
    }

    SimpleContainer container() {
        return container;
    }

    boolean has() {
        return !container.getItem(0).isEmpty();
    }

    void put(ItemStack held) {
        container.setItem(0, held.copyWithCount(1));
    }

    void adopt() {
        container.setItem(0, TcRegistry.jarBrainStack());
    }

    ItemStack take() {
        if (!has()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = container.getItem(0).copy();
        container.setItem(0, ItemStack.EMPTY);
        return taken;
    }

    private void settled() {
        if (!has()) {
            box.unbind();
        }
        Level level = box.getLevel();
        if (level == null || level.isClientSide() || box.isRemoved()) {
            return;
        }
        BlockState live = level.getBlockState(box.getBlockPos());
        boolean present = has();
        if (live.is(box.getBlockState().getBlock()) && live.getValue(BlockGachaBox.JAR) != present) {
            level.setBlock(box.getBlockPos(), live.setValue(BlockGachaBox.JAR, present), 3);
        }
    }

    void save(ValueOutput output) {
        output.store(TAG_BRAIN, ItemStack.OPTIONAL_CODEC, container.getItem(0));
    }

    void load(ValueInput input) {
        container.setItem(0, input.read(TAG_BRAIN, ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY));
    }
}
