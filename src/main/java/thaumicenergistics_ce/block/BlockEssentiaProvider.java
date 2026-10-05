package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.essentiaprovider.BlockEntityEssentiaProvider;

/**
 * The Essentia Provider block: a plain cube with one texture on every side, so it has no properties
 * and no facing, and its blockstate declares a single unconditional variant.
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
