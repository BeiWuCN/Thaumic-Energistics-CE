package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;

public class BlockEssentiaCellWorkbench extends ThEBaseEntityBlock {
    public static final MapCodec<BlockEssentiaCellWorkbench> CODEC =
            simpleCodec(BlockEssentiaCellWorkbench::new);

    public BlockEssentiaCellWorkbench(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BlockEssentiaCellWorkbench> codec() {
        return CODEC;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlockEntityEssentiaCellWorkbench(pos, state);
    }

}
