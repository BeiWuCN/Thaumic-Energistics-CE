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
 * 知识铭刻机方块：把 AE2 样板转成它所编码的奥术配方，写进知识核心，
 * 随后由奥术组装机读取。
 * {@code FACING} 纯属外观，没有分面行为，但模型亮的那一面是正面，
 * 方块状态按这个属性旋转模型。
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
