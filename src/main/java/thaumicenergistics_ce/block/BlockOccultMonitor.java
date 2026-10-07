package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor;

/**
 * The Occult Monitor block: three states, none decorative, and one pulse that is not a state.
 * {@code facing} turns the frame, {@code book} is the thaumonomicon as a real state, and
 * {@code network} is the ME connection; the models come from those three names. The pulse is asked
 * of the machine by the block, and a scheduled tick takes it down again.
 */
public class BlockOccultMonitor extends ThEBaseEntityBlock {

    public static final MapCodec<BlockOccultMonitor> CODEC = simpleCodec(BlockOccultMonitor::new);

    /** The strength of the finished-craft pulse, which is a redstone signal and not an analogue read. */
    private static final int SIGNAL_STRENGTH = 15;

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final BooleanProperty BOOK = BooleanProperty.create("book");

    public static final BooleanProperty NETWORK = BooleanProperty.create("network");

    public BlockOccultMonitor(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BOOK, false)
                .setValue(NETWORK, false));
    }

    @Override
    protected MapCodec<? extends BlockOccultMonitor> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BOOK, NETWORK);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, player.getMainHandItem(), player.isShiftKeyDown());
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        // ItemInteractionResult rather than InteractionResult: the type this hook returns in 1.21.
        // The constant is spelled out because 1.21 renamed it away from a bare PASS.
        InteractionResult result = interact(level, pos, player, stack, player.isShiftKeyDown());
        return result == InteractionResult.SUCCESS
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static InteractionResult interact(
            Level level, BlockPos pos, Player player, ItemStack held, boolean sneaking) {
        if (level.isClientSide()) {
            // Answer optimistically; the server decides. Both reach the same answer, because
            // the condition is one both sides know: what is in hand, and whether sneaking.
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof BlockEntityOccultMonitor monitor)) {
            return InteractionResult.PASS;
        }
        var takenBack = monitor.interact(held, sneaking);
        if (takenBack != null) {
            // The tome came off; hand it to the player, or drop it if there is no room.
            if (!player.getInventory().add(takenBack)) {
                player.drop(takenBack, false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityOccultMonitor(pos, state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    /** The finished-craft pulse: strength 15 while the machine holds it, nothing otherwise. */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof BlockEntityOccultMonitor monitor && monitor.pulsing()
                ? SIGNAL_STRENGTH
                : 0;
    }

    /** Takes the pulse down: the machine scheduled this tick when the ritual finished. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof BlockEntityOccultMonitor monitor) {
            monitor.endPulse();
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BlockEntityOccultMonitor monitor) {
            monitor.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
