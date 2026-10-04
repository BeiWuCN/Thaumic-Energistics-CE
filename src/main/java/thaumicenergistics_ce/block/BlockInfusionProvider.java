package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityInfusionProvider;

/**
 * The Infusion Provider block.
 *
 * <ul>
 * <li>No facing: the altar finds sources by aspect container capability on nearby blocks.</li>
 * <li>The blockstate declares a single unconditional variant.</li>
 * </ul>
 */
public class BlockInfusionProvider extends ThEBaseEntityBlock {

    public static final MapCodec<BlockInfusionProvider> CODEC = simpleCodec(BlockInfusionProvider::new);

    public BlockInfusionProvider(Properties properties) {
        super(properties);
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
