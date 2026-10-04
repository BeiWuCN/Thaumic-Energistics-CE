package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import thaumicenergistics_ce.blockentity.BlockEntityInfusionMonitor;

/**
 * The Infusion Monitor block.
 * <ul>
 *   <li>Three states, none decorative: {@code facing} turns the frame, {@code book} is the
 *       Thaumonomicon and a real state rather than a stored flag, {@code network} is the ME connection.
 *   <li>The blockstate file picks the off, book and lit models from exactly these three names.
 * </ul>
 */
public class BlockInfusionMonitor extends ThEBaseEntityBlock {

    public static final MapCodec<BlockInfusionMonitor> CODEC = simpleCodec(BlockInfusionMonitor::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    /** Whether the Thaumonomicon is in the slot. Lowers the frame's face to show the book. */
    public static final BooleanProperty BOOK = BooleanProperty.create("book");

    /** Whether the monitor is connected to a grid. Lights the model. */
    public static final BooleanProperty NETWORK = BooleanProperty.create("network");

    public BlockInfusionMonitor(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(BOOK, false)
                .setValue(NETWORK, false));
    }

    @Override
    protected MapCodec<? extends BlockInfusionMonitor> codec() {
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

    /**
     * Puts the book on, or takes it off: taking it off is sneak-right-click with an empty hand, so a
     * plain right-click to <em>look</em> at the monitor cannot hand the book back.
     */
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
        if (!(level.getBlockEntity(pos) instanceof BlockEntityInfusionMonitor monitor)) {
            return InteractionResult.PASS;
        }
        var takenBack = monitor.interact(held, sneaking);
        if (takenBack != null) {
            // The book came off; hand it to the player, or drop it if there is no room.
            if (!player.getInventory().add(takenBack)) {
                player.drop(takenBack, false);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityInfusionMonitor(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BlockEntityInfusionMonitor monitor) {
            monitor.dropContents();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
