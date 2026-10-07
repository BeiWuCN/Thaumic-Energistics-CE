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

/** 方块状态带 {@code facing} 和 {@code screen}；{@code jar} 镜像槽里的大脑。 */
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
        // 潜行取回大脑，不管手上拿着什么：空手也得能操作。
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
                // 先解绑盒子：不管取出什么，下一个大脑归新主人。
                box.unbind();
                ItemStack brain = box.takeBrain();
                // 卡片跟着大脑一起取出：空盒子没有可加速的对象。
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
        // 盒里残留的无主大脑（来自中途失败的存档）由点击的玩家认领：
        // 一样都不丢，一次点击就修好了。
        if (level.getBlockEntity(pos) instanceof BlockEntityGachaBox unbound && unbound.hasUnboundBrain()) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            unbound.bind(player);
            player.displayClientMessage(
                    Component.translatable("block.thaumicenergistics_ce.gacha_box.rebound"), true);
            return ItemInteractionResult.SUCCESS;
        }
        // 已装大脑的盒子只收被给的那种大脑，别的放不进去。
        if (!state.getValue(JAR)
                && TcRegistry.isJarBrain(stack)
                && level.getBlockEntity(pos) instanceof BlockEntityGachaBox box) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            // 状态变化由槽位触发，不由方块触发：
            // 实体保留自己的数据，绑定到放入大脑的玩家。
            box.addBrain(stack);
            box.bind(player);
            level.playSound(null, pos, TcRegistry.jarBrainPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return ItemInteractionResult.SUCCESS;
        }
        // 四个槽位只收加速卡，只有它缩短一轮的时间。
        // 别的升级，容量卡或红石卡，一律拒收不存。
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

    /** 把物品给玩家，实在放不下才丢到地上。 */
    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // 破坏盒子也会取出里面的大脑，{@code jar} 要从被替换的那个状态读，不从实体读：
        // 实体可能已经拿到新状态了。
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
