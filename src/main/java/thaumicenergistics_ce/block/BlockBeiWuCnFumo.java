package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;

public class BlockBeiWuCnFumo extends BlockDecorativeFigure {

    public static final MapCodec<BlockBeiWuCnFumo> CODEC = simpleCodec(BlockBeiWuCnFumo::new);

    public BlockBeiWuCnFumo(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BlockBeiWuCnFumo> codec() {
        return CODEC;
    }
}
