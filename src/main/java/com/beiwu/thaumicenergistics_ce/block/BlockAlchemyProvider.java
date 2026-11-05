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
import com.beiwu.thaumicenergistics_ce.blockentity.alchemyprovider.BlockEntityAlchemyProvider;

public class BlockAlchemyProvider extends ThEBaseEntityBlock {

    public static final MapCodec<BlockAlchemyProvider> CODEC = simpleCodec(BlockAlchemyProvider::new);

    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public BlockAlchemyProvider(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected MapCodec<? extends BlockAlchemyProvider> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityAlchemyProvider(pos, state);
    }
}
