package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;

/**
 * 源质元件工作台方块，在这里指定存储元件可以存放哪些要素。
 * 不声明朝向，模型是对称的，加方向属性也转不出任何变化。
 */
public class BlockEssentiaCellWorkbench extends ThEBaseEntityBlock {
    public static final MapCodec<BlockEssentiaCellWorkbench> CODEC =
            simpleCodec(BlockEssentiaCellWorkbench::new);

    public BlockEssentiaCellWorkbench(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BlockEssentiaCellWorkbench> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityEssentiaCellWorkbench(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BlockEntityEssentiaCellWorkbench workbench) {
            workbench.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
