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
 * 奥术样板的物品形态，为的是让待处理的 AE2 合成计划能挺过一次存档。AE2 通过
 * {@link PatternDetailsHelper#decodePattern} 重新加载已保存的任务，它要的是
 * {@code EncodedPatternItem}。定义里带的是配方而不是结果，因为结果在配方之间并不唯一。
 * 相等性同解码一样重要，因为供应器索引是以两者为键的
 * {@code HashMap}，所以相等性错了索引就找不到条目。
 */
public final class ItemArcanePattern extends Item {

    public ItemArcanePattern(Properties properties) {
        super(properties);
    }

    /** 通过 AE2 的 builder 构建该物品，那是 AE2 唯一接受的 {@code EncodedPatternItem} 来源。 */
    public static Item build() {
        return PatternDetailsHelper.encodedPatternItemBuilder(new Decoder()).build();
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
            // 作为定义原样传入，使解码出的任务与这台机器提供的相等。
            return ArcanePatternDetails.of(pattern, registries, null, key);
        }
    }
}
