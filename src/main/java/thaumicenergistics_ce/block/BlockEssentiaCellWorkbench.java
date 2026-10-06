package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;

/**
 * The Essentia Cell Workbench block, where a storage cell is told which aspects it may hold. It
 * declares no facing because the model is symmetric, so a direction property would turn nothing.
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
