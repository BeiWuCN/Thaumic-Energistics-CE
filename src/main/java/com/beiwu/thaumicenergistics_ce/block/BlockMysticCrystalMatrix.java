package com.beiwu.thaumicenergistics_ce.block;

import com.beiwu.thaumicenergistics_ce.registry.TECEBlocks;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public final class BlockMysticCrystalMatrix extends AmethystBlock {
    public static final MapCodec<BlockMysticCrystalMatrix> CODEC = simpleCodec(BlockMysticCrystalMatrix::new);
    private static final Direction[] DIRECTIONS = Direction.values();

    public BlockMysticCrystalMatrix(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<BlockMysticCrystalMatrix> codec() {
        return CODEC;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int quality = quality();
        int chance = switch (quality) {
            case 0 -> 2;
            case 1 -> 3;
            case 2 -> 5;
            default -> 8;
        };
        if (random.nextInt(chance) != 0) {
            return;
        }
        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos targetPos = pos.relative(direction);
        BlockState target = level.getBlockState(targetPos);
        Block next = null;
        if (BuddingAmethystBlock.canClusterGrowAtState(target)) {
            next = TECEBlocks.SMALL_MYSTIC_CRYSTAL_BUD.get();
        } else if (target.hasProperty(AmethystClusterBlock.FACING)
                && target.getValue(AmethystClusterBlock.FACING) == direction) {
            if (target.is(TECEBlocks.SMALL_MYSTIC_CRYSTAL_BUD.get())) {
                next = TECEBlocks.MEDIUM_MYSTIC_CRYSTAL_BUD.get();
            } else if (target.is(TECEBlocks.MEDIUM_MYSTIC_CRYSTAL_BUD.get())) {
                next = TECEBlocks.LARGE_MYSTIC_CRYSTAL_BUD.get();
            } else if (target.is(TECEBlocks.LARGE_MYSTIC_CRYSTAL_BUD.get())) {
                next = TECEBlocks.MYSTIC_CRYSTAL_CLUSTER.get();
            }
        }
        if (next == null) {
            return;
        }
        boolean changed = level.setBlockAndUpdate(targetPos, next.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, target.getFluidState().is(Fluids.WATER)));
        // The highest tier corresponds to AE2's flawless budding quartz.
        if (changed && quality != 0
                && random.nextInt(appeng.decorative.solid.BuddingCertusQuartzBlock.DECAY_CHANCE) == 0) {
            Block degraded = switch (quality) {
                case 1 -> TECEBlocks.CHIPPED_BUDDING_THAUMIC_CRYSTAL.get();
                case 2 -> TECEBlocks.DAMAGED_BUDDING_THAUMIC_CRYSTAL.get();
                default -> TECEBlocks.MYSTIC_CRYSTAL_BLOCK.get();
            };
            level.setBlockAndUpdate(pos, degraded.defaultBlockState());
        }
    }

    private int quality() {
        if (this == TECEBlocks.FLAWLESS_BUDDING_THAUMIC_CRYSTAL.get()) return 0;
        if (this == TECEBlocks.FLAWED_BUDDING_THAUMIC_CRYSTAL.get()) return 1;
        if (this == TECEBlocks.CHIPPED_BUDDING_THAUMIC_CRYSTAL.get()) return 2;
        return 3;
    }
}
