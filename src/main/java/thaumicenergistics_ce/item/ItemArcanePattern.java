package thaumicenergistics_ce.item;

import appeng.api.crafting.EncodedPatternDecoder;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.arcane.ThEArcanePattern;

/**
 * 奥术样板的物品形态，待处理的 AE2 合成计划才能挺过存档。AE2 经
 * {@link PatternDetailsHelper#decodePattern} 重载已存任务，它要 {@code EncodedPatternItem}。
 * 定义里带配方不带结果，结果在配方之间不唯一。相等和解码一样要紧，
 * 供应器索引是以两者为键的 {@code HashMap}。
 */
public final class ItemArcanePattern extends Item {

    public ItemArcanePattern(Properties properties) {
        super(properties);
    }

    /**
     * 经 AE2 的 builder 构建该物品，那是 AE2 唯一接受的 {@code EncodedPatternItem} 来源。
     * <ul>
     *   <li>properties 作为参数传入而不在这里构造：26.x 起注册 id 挂在 {@code Item.Properties} 上，
     *       物品构造器经 {@code Item$Properties.effectiveDescriptionId} 读回它。调用方是
     *       {@code DeferredRegister.Items.registerItem}，只有那个重载会盖上 id——在这个方法里构造
     *       properties 会交出一个没有 id 的实例，加载时直接 {@code NullPointerException: Item id not set} 死掉。
     * </ul>
     */
    public static Item build(Item.Properties properties) {
        return PatternDetailsHelper.encodedPatternItemBuilder(new Decoder()).build(properties);
    }

    private static final class Decoder implements EncodedPatternDecoder<ArcanePatternDetails> {

        @Override
        public @Nullable ArcanePatternDetails decode(AEItemKey key, Level level) {
            if (key == null || level == null) {
                return null;
            }
            HolderLookup.Provider registries = level.registryAccess();
            ThEArcanePattern pattern = ThEArcanePattern.ofItem(key.getReadOnlyStack(), registries);
            if (pattern == null) {
                return null;
            }
            // 作为定义原样传入，解码出的任务才等于这台机器的提供。
            return ArcanePatternDetails.of(pattern, registries, null, key);
        }
    }
}
