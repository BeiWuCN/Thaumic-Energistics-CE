package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics_ce.block.BlockGachaBox;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/**
 * The brain's own slot, the {@code jar} blockstate that mirrors it, and what leaving the slot means.
 * Only a jar brain goes in, and never a stack of them. A hopper or a pipe may fill this slot but
 * never empty it: the brain leaves by the bound player's own hand, and that hand unbinds the box.
 */
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

    /** Puts one brain in, copied so the stack the player is holding stays their own. */
    void put(ItemStack held) {
        container.setItem(0, held.copyWithCount(1));
    }

    /** Puts a brain in out of thin air: what a save that kept it in the blockstate alone needs. */
    void adopt() {
        container.setItem(0, TcRegistry.jarBrainStack());
    }

    /** Empties the slot and gives back what was in it, or nothing at all. */
    ItemStack take() {
        if (!has()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = container.getItem(0).copy();
        container.setItem(0, ItemStack.EMPTY);
        return taken;
    }

    /** The blockstate follows the slot, since that is what a client sees and the renderer draws. A
     * box already removed is skipped: taken apart, it must not write its own block back. */
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

    void save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.put(TAG_BRAIN, container.getItem(0).saveOptional(registries));
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        container.setItem(0, ItemStack.parseOptional(registries, tag.getCompound(TAG_BRAIN)));
    }
}
