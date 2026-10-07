package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;

/** The upper half: symmetric model, so no facing; the state only says whether it stands on a body. */
public class BlockGachaBoxAggregator extends Block {

    public static final MapCodec<BlockGachaBoxAggregator> CODEC =
            simpleCodec(BlockGachaBoxAggregator::new);

    public static final BooleanProperty STACKED = BooleanProperty.create("stacked");

    public BlockGachaBoxAggregator(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STACKED, false));
    }

    @Override
    protected MapCodec<? extends BlockGachaBoxAggregator> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STACKED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(STACKED, isOnBody(context.getLevel(), context.getClickedPos()));
    }

    // The body can be placed or broken under a standing upper half, so the state follows it.
    @Override
    public void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighborBlock,
            BlockPos neighborPos,
            boolean movedByPiston) {
        boolean stacked = isOnBody(level, pos);
        if (stacked != state.getValue(STACKED)) {
            level.setBlockAndUpdate(pos, state.setValue(STACKED, stacked));
        }
    }

    private static boolean isOnBody(Level level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof BlockGachaBox;
    }
}
