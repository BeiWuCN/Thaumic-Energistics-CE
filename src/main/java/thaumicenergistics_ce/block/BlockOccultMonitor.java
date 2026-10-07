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
 * 神秘监控器方块：三个状态都不可少，外加一个不是状态的脉冲。
 * {@code facing} 转外框，{@code book} 是作为真状态的魔导手册，{@code network} 是 ME 连接；
 * 模型就来自这三个名字。脉冲由方块向机器问，再由计划 tick 降下来。
 */
public class BlockOccultMonitor extends ThEBaseEntityBlock {

    public static final MapCodec<BlockOccultMonitor> CODEC = simpleCodec(BlockOccultMonitor::new);

    /** 合成完成脉冲的强度，它是红石信号不是模拟量读数。 */
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
        // 返回 [ItemInteractionResult] 不是 [InteractionResult]：1.21 这个钩子的返回类型。
        // 常量写全，1.21 把它从裸的 [PASS] 改了名。
        InteractionResult result = interact(level, pos, player, stack, player.isShiftKeyDown());
        return result == InteractionResult.SUCCESS
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static InteractionResult interact(
            Level level, BlockPos pos, Player player, ItemStack held, boolean sneaking) {
        if (level.isClientSide()) {
            // 乐观作答，最终由服务端定。两端答案一样：条件双方都知道，手上拿什么、有没有潜行。
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof BlockEntityOccultMonitor monitor)) {
            return InteractionResult.PASS;
        }
        var takenBack = monitor.interact(held, sneaking);
        if (takenBack != null) {
            // 手册被取下；交给玩家，没空间就掉地上。
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

    /** 合成完成脉冲：机器持着时强度 15，其余没有信号。 */
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        return level.getBlockEntity(pos) instanceof BlockEntityOccultMonitor monitor && monitor.pulsing()
                ? SIGNAL_STRENGTH
                : 0;
    }

    /** 把脉冲降下来：仪式结束时机器计划了这个 tick。 */
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
