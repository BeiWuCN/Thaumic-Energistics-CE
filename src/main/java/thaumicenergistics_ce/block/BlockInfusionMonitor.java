package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 *
 * <p>Three properties, because the model has three states and they are not decorative: {@code facing} turns
 * the frame, {@code book} is whether the Thaumonomicon has been placed on it, and {@code network} is
 * whether it is connected to an ME network. The blockstate file in this mod's assets selects between the
 * off, book and lit models from exactly these, so all three have to exist and be named as written - a
 * multipart variant no state can match is simply never drawn.
 *
 * <p>{@code book} is the interesting one. It is a real state rather than a stored flag because the model
 * has to change the moment the book is placed, and because a player looking at the block should be able to
 * see whether it is armed without opening anything.
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
     * Puts the book on, or takes it off.
     *
     * <h2>Taking it off is the half that had to become deliberate</h2>
     *
     * <p>This used to mean "holding a Thaumonomicon places it, anything else takes it back", with no other
     * condition - so a player who right-clicked the monitor to <em>look</em> at it, with anything in hand or
     * with nothing at all, had the book handed straight back. Reported as "right-clicking takes my
     * Thaumonomicon off the machine": the machine undoing the one thing it is built for.
     *
     * <p>Taking the book back is now <b>sneak-right-click with an empty hand</b>, a gesture that cannot
     * happen by accident while placing, reading or fiddling. Placing still needs no modifier, because that
     * is the action with a book in hand and there is nothing ambiguous about it.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, player.getMainHandItem(), player.isShiftKeyDown());
    }

    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(
            net.minecraft.world.item.ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            net.minecraft.world.InteractionHand hand,
            BlockHitResult hit) {
        // ItemInteractionResult rather than InteractionResult: that is the type this hook returns in 1.21,
        // and the two are not interchangeable even though both describe "what happened". The pass constant
        // is spelled out in full because 1.21 renamed it away from a bare PASS.
        InteractionResult result = interact(level, pos, player, stack, player.isShiftKeyDown());
        return result == InteractionResult.SUCCESS
                ? net.minecraft.world.ItemInteractionResult.SUCCESS
                : net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static InteractionResult interact(
            Level level, BlockPos pos, Player player, net.minecraft.world.item.ItemStack held, boolean sneaking) {
        if (level.isClientSide()) {
            // Answer optimistically; the server decides. Both reach the same answer, because the condition
            // is one both sides know: what is in hand, and whether the player is sneaking.
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
