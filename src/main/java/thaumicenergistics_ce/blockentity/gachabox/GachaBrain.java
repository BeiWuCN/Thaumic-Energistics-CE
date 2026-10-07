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
 * 脑自己的槽位、镜像它的 {@code jar} 方块状态，以及离开槽位意味着什么。
 * 只有缸中之脑能放入，且永远不能是一叠。
 * 漏斗或管道能填这个槽位，永远不能清空它：脑由已绑定玩家亲自动手离开，
 * 那一手会解除箱子的绑定。
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

    /** 放入一颗脑，复制一份，玩家手里拿的堆仍是他自己的。 */
    void put(ItemStack held) {
        container.setItem(0, held.copyWithCount(1));
    }

    /** 凭空放入一颗脑：脑只存在方块状态里的存档需要这个。 */
    void adopt() {
        container.setItem(0, TcRegistry.jarBrainStack());
    }

    /** 清空槽位，交回里面原有的东西，没有就交回空。 */
    ItemStack take() {
        if (!has()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = container.getItem(0).copy();
        container.setItem(0, ItemStack.EMPTY);
        return taken;
    }

    /** 方块状态跟着槽位走，客户端看到、渲染器画的就是它。
     * 已经移除的箱子跳过：被拆开之后它绝不能把自己的方块写回去。 */
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
