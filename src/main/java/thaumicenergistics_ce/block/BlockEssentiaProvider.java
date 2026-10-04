package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaProvider;

/**
 * The Essentia Provider block: a plain cube with one texture on every side, so it has no properties
 * and no facing.
 * <ul>
 * <li>A direction property would be a state that changes nothing, and states that change nothing
 * must still be kept in step for no reason.</li>
 * <li>The blockstate file declares a single unconditional variant, all a block with no properties
 * needs.</li>
 * </ul>
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
