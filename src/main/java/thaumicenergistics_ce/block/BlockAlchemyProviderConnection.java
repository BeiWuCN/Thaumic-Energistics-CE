package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProviderConnection;

/**
 * 炼金供应器连接件：无线源质链路的接收端。{@code facing} 把插头指向它装的那个面，
 * 本 mod 资源里的方块状态针对它声明了全部六个方向，属性和它的名字都是关键。
 * {@code connected} 是链路在不在；插头能装在任意面，向上向下都行。
 */
public class BlockAlchemyProviderConnection extends ThEBaseEntityBlock {

    public static final MapCodec<BlockAlchemyProviderConnection> CODEC =
            simpleCodec(BlockAlchemyProviderConnection::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public BlockAlchemyProviderConnection(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTED, false));
    }

    @Override
    protected MapCodec<? extends BlockAlchemyProviderConnection> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityAlchemyProviderConnection(pos, state);
    }

    /**
     * 方块 tick 器搬源质：它没有自己的网格节点，它是线不是机器，故没法像供应器那样让 AE2 来 tick 它；
     * 工作量是每半秒一次传输。
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof BlockEntityAlchemyProviderConnection receiver) {
                receiver.serverTick();
            }
        };
    }
}
