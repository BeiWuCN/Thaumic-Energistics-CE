package com.beiwu.thaumicenergistics_ce.block;

import com.beiwu.thaumicenergistics_ce.registry.TECEBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** The complete machine's upper occupied space; never a separate item or model. */
public final class BlockGachaBoxSpace extends Block {
    public static final MapCodec<BlockGachaBoxSpace> CODEC = simpleCodec(BlockGachaBoxSpace::new);

    public BlockGachaBoxSpace(Properties properties) { super(properties); }

    @Override
    protected MapCodec<? extends BlockGachaBoxSpace> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return new ItemStack(TECEBlocks.GACHA_BOX.get());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        BlockState base = level.getBlockState(pos.below());
        return base.getBlock() instanceof BlockGachaBox box
                ? box.useItemOn(stack, base, level, pos.below(), player, hand, hit)
                : InteractionResult.PASS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        BlockState base = level.getBlockState(pos.below());
        return base.getBlock() instanceof BlockGachaBox box
                ? box.useWithoutItem(base, level, pos.below(), player, hit) : InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && level.getBlockState(pos.below()).is(TECEBlocks.GACHA_BOX.get())) {
            level.destroyBlock(pos.below(), !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean piston) {
        super.affectNeighborsAfterRemoval(state, level, pos, piston);
        if (!level.getBlockState(pos).is(this) && level.getBlockState(pos.below()).is(TECEBlocks.GACHA_BOX.get())) {
            level.destroyBlock(pos.below(), true);
        }
    }
}
