package com.beiwu.thaumicenergistics_ce.block;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;

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
import com.beiwu.thaumicenergistics_ce.blockentity.gachabox.BlockEntityGachaBox;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcRegistry;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import com.beiwu.thaumicenergistics_ce.registry.TECEBlocks;

/** 方块状态带 {@code facing} 和 {@code screen}；{@code jar} 镜像槽里的大脑。 */
public class BlockGachaBox extends ThEBaseEntityBlock {

    public static final MapCodec<BlockGachaBox> CODEC = simpleCodec(BlockGachaBox::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public static final EnumProperty<Screen> SCREEN = EnumProperty.create("screen", Screen.class);

    public static final BooleanProperty JAR = BooleanProperty.create("jar");
    public static final BooleanProperty FLUIX_BRAIN = BooleanProperty.create("fluix_brain");

    public BlockGachaBox(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(SCREEN, Screen.OFF)
                .setValue(JAR, false)
                .setValue(FLUIX_BRAIN, false));
    }

    @Override
    protected MapCodec<? extends BlockGachaBox> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SCREEN, JAR, FLUIX_BRAIN);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedPos().getY() + 1 >= context.getLevel().getMaxBuildHeight()
                || !context.getLevel().getBlockState(context.getClickedPos().above()).canBeReplaced(context)) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    public static void ensureUpperSpace(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (!level.isClientSide() && above.getY() < level.getMaxBuildHeight()
                && level.getBlockState(above).canBeReplaced()) {
            level.setBlockAndUpdate(above, com.beiwu.thaumicenergistics_ce.registry.TECEBlocks.GACHA_BOX_SPACE.get().defaultBlockState());
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean piston) {
        super.onPlace(state, level, pos, old, piston);
        if (!old.is(this)) ensureUpperSpace(level, pos);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (world, pos, current, entity) -> {
            if (world.getGameTime() % 20 == 0 && entity instanceof BlockEntityGachaBox box) {
                ensureUpperSpace(world, pos);
                box.refreshScreen();
            }
        };
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
        InteractionResult result = interact(level, pos, player, stack, state);
        return result == InteractionResult.SUCCESS ? ItemInteractionResult.SUCCESS : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return interact(level, pos, player, player.getMainHandItem(), state);
    }

    private static InteractionResult interact(
            Level level, BlockPos pos, Player player, ItemStack stack, BlockState state) {
        // Handle the upgrade before sneak-removal, so holding the upgrade never dismantles the jar.
        if (stack.is(TECEItems.FLUIX_BRAIN.get())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(level.getBlockEntity(pos) instanceof BlockEntityGachaBox box)) return InteractionResult.PASS;
            if (!box.mayTakeBrain(player)) {
                player.displayClientMessage(Component.translatable("block.thaumicenergistics_ce.gacha_box.bound_to_other", box.ownerName()), true);
            } else if (box.upgradeBrain()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                give(player, TcRegistry.zombieBrainStack());
                level.playSound(null, pos, TcRegistry.jarBrainPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown()) {
            if (!state.getValue(JAR)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            if (level.getBlockEntity(pos) instanceof BlockEntityGachaBox box) {
                if (!box.mayTakeBrain(player)) {
                    player.displayClientMessage(
                            Component.translatable(
                                    "block.thaumicenergistics_ce.gacha_box.bound_to_other",
                                    box.ownerName() == null ? "" : box.ownerName()),
                            true);
                    return InteractionResult.SUCCESS;
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
            return InteractionResult.SUCCESS;
        }
        // 盒里残留的无主大脑（来自中途失败的存档）由点击的玩家认领：
        // 一样都不丢，一次点击就修好了。
        if (level.getBlockEntity(pos) instanceof BlockEntityGachaBox unbound && unbound.hasUnboundBrain()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            unbound.bind(player);
            player.displayClientMessage(
                    Component.translatable("block.thaumicenergistics_ce.gacha_box.rebound"), true);
            return InteractionResult.SUCCESS;
        }
        // 已装大脑的盒子只收被给的那种大脑，别的放不进去。
        if (!state.getValue(JAR)
                && TcRegistry.isJarBrain(stack)
                && level.getBlockEntity(pos) instanceof BlockEntityGachaBox box) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            // 状态变化由槽位触发，不由方块触发：
            // 实体保留自己的数据，绑定到放入大脑的玩家。
            box.addBrain(stack);
            box.bind(player);
            level.playSound(null, pos, TcRegistry.jarBrainPlaceSound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        // 四个槽位只收加速卡，只有它缩短一轮的时间。
        // 别的升级，容量卡或红石卡，一律拒收不存。
        if (AEItems.SPEED_CARD.is(stack)
                && level.getBlockEntity(pos) instanceof BlockEntityGachaBox box
                && box.hasRoomForCard()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            box.addCard(stack);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.7F, 1.0F);
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
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
        if (!level.isClientSide && !state.is(newState.getBlock())
                && level.getBlockState(pos.above()).is(TECEBlocks.GACHA_BOX_SPACE.get())) {
            level.removeBlock(pos.above(), false);
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
