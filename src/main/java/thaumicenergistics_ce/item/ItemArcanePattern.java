package thaumicenergistics_ce.item;

import appeng.api.crafting.EncodedPatternDecoder;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.ArcanePatternDetails;

/**
 * Item form of an arcane pattern, needed so a pending AE2 crafting plan survives a save.
 * <ul>
 *   <li>AE2 reloads a saved task through {@link PatternDetailsHelper#decodePattern}, which accepts only
 *       an {@code EncodedPatternItem}. Any other item makes {@code ExecutingCraftingJob} silently drop
 *       the task, leaving a plan that can never advance.
 *   <li>The definition carries the recipe, not the result: results are not unique among recipes.
 *   <li>Equality matters as much as decoding: the provider index is a {@code HashMap} keyed by
 *       {@code equals}/{@code hashCode}, both derived from the definition, so an unequal key misses.
 * </ul>
 */
public final class ItemArcanePattern extends Item {

    public ItemArcanePattern(Properties properties) {
        super(properties);
    }

    /** Builds the item through AE2's builder, the only source of the {@code EncodedPatternItem} AE2 takes. */
    public static Item build() {
        return PatternDetailsHelper.encodedPatternItemBuilder(new Decoder()).build();
    }

    /** Reads a pattern back out of its item form, through the assembler's own adapter. */
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
