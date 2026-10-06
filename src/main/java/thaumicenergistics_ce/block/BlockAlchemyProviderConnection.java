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
 * The Alchemy Provider Connection: the receiving end of a wireless essentia link.
 * <ul>
 *   <li>{@code facing} points the plug at the surface it is mounted on; the blockstate in this mod's
 *       assets declares all six directions against it, so both the property and its name are load-bearing.
 *   <li>{@code connected} is whether a link exists, and it mounts on any surface, up and down included.
 * </ul>
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
     * A block ticker carries the essentia: it has no grid node of its own - it is a wire, not a machine - so
     * it cannot ask AE2 to tick it the way the provider does, and the work is one transfer per half second.
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
