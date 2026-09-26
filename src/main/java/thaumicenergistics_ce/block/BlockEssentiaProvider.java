package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaProvider;

/**
 * The Essentia Provider block.
 *
 * <p>No facing and no properties. The model is a plain cube with one texture on every side, so a direction
 * property would be a state that changes nothing - and states that change nothing are states that have to
 * be kept in step for no reason. The blockstate file in this mod's assets already declares a single
 * unconditional variant, which is what a block with no properties needs.
 */
public class BlockEssentiaProvider extends ThEBaseEntityBlock {

    public static final MapCodec<BlockEssentiaProvider> CODEC = simpleCodec(BlockEssentiaProvider::new);

    public BlockEssentiaProvider(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BlockEssentiaProvider> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityEssentiaProvider(pos, state);
    }
}
