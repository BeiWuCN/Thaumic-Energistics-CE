package thaumicenergistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.blockentity.BlockEntityEssentiaCellWorkbench;

/**
 * The Essentia Cell Workbench block.
 *
 * <p>Where a storage cell is told which aspects it may hold. The block has no facing: its model is
 * symmetric, so there is nothing for a direction property to turn, and a property that does nothing is
 * only a state to keep in step.
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
