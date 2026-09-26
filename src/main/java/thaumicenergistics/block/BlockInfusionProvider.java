package thaumicenergistics.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.blockentity.BlockEntityInfusionProvider;

/**
 * The Infusion Provider block.
 *
 * <p>No facing. The altar finds essentia sources by looking for the aspect container capability on nearby
 * blocks and does not care which way any of them point, so a direction property would be a state with no
 * meaning. The blockstate file in this mod's assets already declares a single unconditional variant.
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
