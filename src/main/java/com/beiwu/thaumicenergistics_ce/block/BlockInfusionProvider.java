package com.beiwu.thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;

/**
 * 注魔供应器方块。不声明朝向：祭坛通过附近方块上的要素容器 capability 找来源，
 * 它的方块状态只有一个无条件变体。
 */
public class BlockInfusionProvider extends ThEBaseEntityBlock {

    public static final MapCodec<BlockInfusionProvider> CODEC = simpleCodec(BlockInfusionProvider::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BlockInfusionProvider(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected MapCodec<? extends BlockInfusionProvider> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityInfusionProvider(pos, state);
    }
}
