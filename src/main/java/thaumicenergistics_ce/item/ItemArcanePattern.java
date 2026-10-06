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
 * Item form of an arcane pattern, needed so a pending AE2 crafting plan survives a save. AE2
 * reloads a saved task through {@link PatternDetailsHelper#decodePattern}, which wants an
 * {@code EncodedPatternItem}. The definition carries the recipe, not the result, because results
 * are not unique among recipes. Equality matters as much as decoding, because the provider index
 * is a {@code HashMap} keyed by both.
 */
public final class ItemArcanePattern extends Item {

    public ItemArcanePattern(Properties properties) {
        super(properties);
    }

    /** Builds the item through AE2's builder, the only source of the {@code EncodedPatternItem} AE2 takes. */
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
            // Passed through as the definition so the decoded task equals this machine's offer.
            return ArcanePatternDetails.of(pattern, registries, null, key);
        }
    }
}
