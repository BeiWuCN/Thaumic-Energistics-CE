package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;

/**
 * The Knowledge Inscriber block: turns an AE2 pattern into the arcane recipe it encodes and writes
 * that recipe into a knowledge core, which an Arcane Assembler then reads.
 * <ul>
 * <li>{@code FACING} is cosmetic - no sided behaviour - but the model's bright face is the front, so
 * the blockstate rotates the model by this property.</li>
 * </ul>
 */
public class BlockKnowledgeInscriber extends ThEBaseEntityBlock {
    public static final MapCodec<BlockKnowledgeInscriber> CODEC = simpleCodec(BlockKnowledgeInscriber::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public BlockKnowledgeInscriber(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockKnowledgeInscriber> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityKnowledgeInscriber(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BlockEntityKnowledgeInscriber inscriber) {
            inscriber.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
