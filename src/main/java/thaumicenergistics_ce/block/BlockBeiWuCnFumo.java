package thaumicenergistics_ce.block;

import com.mojang.serialization.MapCodec;

/**
 * 第二个装饰人偶。行为与 {@link BlockDecorativeFigure} 完全一样，另开一个类只为让注册名与方块状态表独立。
 * 模型只有坐姿一种姿态，{@code variant} 恒为 {@code false}，方块状态 JSON 只声明四个朝向。
 */
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
