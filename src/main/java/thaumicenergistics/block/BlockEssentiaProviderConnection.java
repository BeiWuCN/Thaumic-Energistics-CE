package thaumicenergistics.block;

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
import thaumicenergistics.blockentity.BlockEntityEssentiaProviderConnection;

/**
 * The Essentia Provider Connection: the receiving end of a wireless essentia link.
 *
 * <p>Two properties. {@code facing} turns the plug towards whatever surface it is on - the blockstate in
 * this mod's assets declares all six directions against it, so the property has to exist and be named
 * {@code facing} for the model to be drawn at all. {@code connected} is whether a link has been made, and
 * the blockstate draws a lit ring for it.
 *
 * <p>Mounts on any surface, including up and down, which is why this one uses the full six-direction
 * property rather than the horizontal one the other machines use. A receiver's whole purpose is to be
 * somewhere awkward.
 */
public class BlockEssentiaProviderConnection extends ThEBaseEntityBlock {

    public static final MapCodec<BlockEssentiaProviderConnection> CODEC =
            simpleCodec(BlockEssentiaProviderConnection::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    /** Whether a provider is bound. Lights the ring. */
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public BlockEssentiaProviderConnection(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONNECTED, false));
    }

    @Override
    protected MapCodec<? extends BlockEssentiaProviderConnection> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CONNECTED);
    }

    /**
     * Points the plug into the surface it was placed against.
     *
     * <p>The model is built facing north, so {@code FACING} has to be the direction the plug points
     * <em>away</em> from the block it is mounted on - which is the side the player clicked, the opposite of
     * where they were standing. Getting this backwards would put every receiver inside the wall.
     */
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
        return new BlockEntityEssentiaProviderConnection(pos, state);
    }

    /**
     * The receiver needs a tick loop to carry essentia.
     *
     * <p>It has no grid node of its own - it is a wire, not a machine - so it cannot ask AE2 to tick it the
     * way the provider does. A block ticker is what is left, and it is the right tool: the work is a
     * transfer every half second, not a machine that has to react to a network.
     */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof BlockEntityEssentiaProviderConnection receiver) {
                receiver.serverTick();
            }
        };
    }
}
