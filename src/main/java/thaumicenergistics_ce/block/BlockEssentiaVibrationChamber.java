package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaVibrationChamber;

/**
 * The Essentia Vibration Chamber block.
 *
 * <p>Has a facing, unlike the cell workbench, because its model is not symmetric: the front face is the
 * one with the animated aspect texture, and the top is the input. The blockstate file in the mod's assets
 * already carries the four horizontal variants, so the property has to exist and has to be named exactly
 * {@code facing} for those variants to be reachable at all - a blockstate whose variants no state can match
 * is a missing-texture cube.
 *
 * <p>Horizontal only. The model is a full cube with a distinct top and bottom, so a vertical facing would
 * put the input texture on a side and the front on the top, which is not what the art draws.
 */
public class BlockEssentiaVibrationChamber extends ThEBaseEntityBlock {

    public static final MapCodec<BlockEssentiaVibrationChamber> CODEC =
            simpleCodec(BlockEssentiaVibrationChamber::new);

    /** Which way the animated front face points. Matches the blockstate variants. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public BlockEssentiaVibrationChamber(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BlockEssentiaVibrationChamber> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Placed facing the player, so the animated face is the one you see when you put it down. */
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
     * Right-clicking opens the machine's screen.
     *
     * <p>Overridden rather than inherited: {@code ThEBaseEntityBlock} opens a menu for the machines built on
     * {@code ThEBaseBlockEntity}, and this one is a grid machine and so is built on AE2's block entity
     * instead. It is a {@code MenuProvider} all the same, and this is the one line that says so.
     */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state,
            net.minecraft.world.level.Level level,
            BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide()) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof BlockEntityEssentiaVibrationChamber chamber
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            serverPlayer.openMenu(chamber, buffer -> buffer.writeBlockPos(pos));
            return net.minecraft.world.InteractionResult.CONSUME;
        }
        return net.minecraft.world.InteractionResult.PASS;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityEssentiaVibrationChamber(pos, state);
    }
}
