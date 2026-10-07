package thaumicenergistics_ce.block;

import appeng.core.definitions.AEItems;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;
import thaumicenergistics_ce.compat.thaumaturge.TcRegistry;

/** State carries {@code facing} and {@code screen}; {@code jar} mirrors the brain sitting in its slot. */
public class BlockGachaBox extends ThEBaseEntityBlock {

    public static final MapCodec<BlockGachaBox> CODEC = simpleCodec(BlockGachaBox::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final EnumProperty<Screen> SCREEN = EnumProperty.create("screen", Screen.class);

    public static final BooleanProperty JAR = BooleanProperty.create("jar");

    public BlockGachaBox(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SCREEN, Screen.OFF)
                .setValue(JAR, false));
    }

    @Override
    protected MapCodec<? extends BlockGachaBox> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SCREEN, JAR);
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
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        // Sneaking takes the brain back, whatever is held: a bare hand has to be able to do it.
        if (player.isShiftKeyDown()) {
            if (!state.getValue(JAR)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (level.getBlockEntity(pos) instanceof BlockEntityGachaBox box) {
                if (!box.mayTakeBrain(player)) {
                    player.displayClientMessage(
                            Component.translatable(
                                    "block.thaumicenergistics_ce.gacha_box.bound_to_other",
                                    box.ownerName() == null ? "" : box.ownerName()),
                            true);
                    return ItemInteractionResult.SUCCESS;
                }
                // The box is unbound first: whatever comes out, the next brain is a new owner's.
                box.unbind();
                ItemStack brain = box.takeBrain();
                // The cards come out with the brain: an empty box has nothing to speed up.
                for (ItemStack card : box.takeCards()) {
                    give(player, card);
                }
                give(player, brain.isEmpty() ? TcRegistry.jarBrainStack() : brain);
            }
            level.setBlockAndUpdate(pos, state.setValue(JAR, false));
            player.displayClientMessage(
                    Component.translatable("block.thaumicenergistics_ce.gacha_box.lost_target"), true);
            return ItemInteractionResult.SUCCESS;
        }
        // A brain that is in the box but belongs to nobody, from a save that failed half way, is
        // claimed by the player who clicks the box: nothing is thrown away and the fix is one click.
        if (level.getBlockEntity(pos) instanceof BlockEntityGachaBox unbound && unbound.hasUnboundBrain()) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            unbound.bind(player);
            player.displayClientMessage(
                    Component.translatable("block.thaumicenergistics_ce.gacha_box.rebound"), true);
            return ItemInteractionResult.SUCCESS;
        }
        // A box that already holds a brain takes the brain it is offered: nothing else goes in.
        if (!state.getValue(JAR)
                && TcRegistry.isJarBrain(stack)
                && level.getBlockEntity(pos) instanceof BlockEntityGachaBox box) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            // The slot carries the blockstate change but the block does not, so the entity keeps its
            // tile and is bound to the player who put the brain in.
            box.addBrain(stack);
            box.bind(player);
            level.playSound(null, pos, TcRegistry.jarBrainPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }
        // Only speed cards go in the four slots: they are the ones that shorten a turn. Any other
        // upgrade, a capacity or a redstone card among them, is refused rather than stored.
        if (AEItems.SPEED_CARD.is(stack)
                && level.getBlockEntity(pos) instanceof BlockEntityGachaBox box
                && box.hasRoomForCard()) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            box.addCard(stack);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** Hands an item over, and puts it on the ground only if the player has nowhere to keep it. */
    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // Breaking the box takes the brain out of it too, so the jar is read from the state being
        // replaced rather than from the entity, which may already have been told about the new one.
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof BlockEntityGachaBox box) {
            box.dropContents(state.getValue(JAR));
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityGachaBox(pos, state);
    }

    public enum Screen implements StringRepresentable {
        OFF("off"),
        ON("on"),
        WORKING("working"),
        FAILED("failed"),
        SUCCESS("success");

        private final String name;

        Screen(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return this.name;
        }
    }
}
