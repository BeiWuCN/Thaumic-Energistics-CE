package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The decorative figure block - a plush likeness, not a machine.
 * <ul>
 *   <li>{@code variant} must stay: the blockstate declares four facings times two, so a missing
 *       variant is unreachable and renders as a missing-texture cube.
 *   <li>The figure stays settable: right-click turns it to face the player who asked.
 * </ul>
 */
public class BlockDecorativeFigure extends HorizontalDirectionalBlock {

    public static final MapCodec<BlockDecorativeFigure> CODEC = simpleCodec(BlockDecorativeFigure::new);

    /** Which of the two models to draw. See the class note for why this exists at all. */
    public static final BooleanProperty VARIANT = BooleanProperty.create("variant");

    /**
     * The figure's box: smaller than a full cube and off-centre, because a full collision shape would
     * feel like an invisible wall around a figure sitting on the floor.
     */
    private static final VoxelShape SHAPE = Shapes.box(0.25, 0.0, 0.25, 0.75, 0.75, 0.75);

    public BlockDecorativeFigure(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(VARIANT, false));
    }

    @Override
    protected MapCodec<? extends BlockDecorativeFigure> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VARIANT);
    }

    @Override
    protected VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
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
     * Turning, or picking up: two things on one button would be ambiguous, so shift is the distinction -
     * a plain click turns the figure to face the player, a shift-click takes it back.
     */
    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide()) {
                // Taken as an item rather than broken, which would play a break sound: a prop being moved.
                if (!player.getAbilities().instabuild) {
                    Block.popResource(level, pos, new ItemStack(asItem()));
                }
                level.removeBlock(pos, false);
            }
            return InteractionResult.SUCCESS;
        }

        if (!level.isClientSide()) {
            BlockState turned = state.setValue(FACING, player.getDirection().getOpposite());
            level.setBlock(pos, turned, 3);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
