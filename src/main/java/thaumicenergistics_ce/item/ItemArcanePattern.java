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
 * The item form of an arcane pattern: what an arcane pattern looks like when it has to be an item.
 *
 * <p><b>Why this exists.</b> {@code ArcanePatternDetails} is the AE2 view of a recipe, and it was enough for
 * one crafting session - but not for a crafting <em>plan</em>. AE2's CPU saves a pending task as nothing but
 * {@code getDefinition().toTag(...)} and rebuilds it on load by handing that tag to
 * {@code PatternDetailsHelper.decodePattern}. That call only answers for a stack whose item is an
 * {@code EncodedPatternItem}; for anything else it returns null, and {@code ExecutingCraftingJob} then drops
 * the task from its map <em>without touching the job's output or waiting-for list</em>. What is left is a plan
 * that was never cancelled and can never advance: the CPU holds a job, has nothing to push, and no line
 * anywhere says why. That is the report - *"重新进入存档后，奥术装配室不能继续CPU合成计划"* - and it is what
 * players call a *"伪合成"*.
 *
 * <p><b>Why the definition had to change, and not just the decoder.</b> Registering a decoder alone cannot
 * work, because the definition it would be handed is whatever {@code getDefinition()} returns - and that used
 * to be the <em>crafting result</em>. A recipe's result is not unique among recipes, so a decoder given a bare
 * "wand" could not tell which pattern was meant. The definition has to be a pattern item carrying the recipe,
 * which is what makes one task distinguishable from another; the decoder then reads that recipe back.
 *
 * <p><b>The decoder registers itself.</b> No call to {@code PatternDetailsHelper.registerDecoder} is needed:
 * AE2's own {@code AEPatternDecoder} is already in that list and forwards to {@code EncodedPatternItem.decode},
 * which owns the decoder given here. So building through {@code encodedPatternItemBuilder} is the whole of the
 * registration.
 *
 * <p><b>Why equality matters as much as decoding.</b> The provider index is a {@code HashMap} keyed by
 * {@code IPatternDetails} {@code equals}/{@code hashCode}. A decoded task therefore only reaches this machine
 * if it is <em>equal</em> to a pattern the machine registered. {@code ArcanePatternDetails} derives both from
 * its definition, so decoding the same item rebuilds an equal object and the lookup resolves - a decoder that
 * minted an unequal key would decode successfully and then find no machine willing to run the task.
 */
public final class ItemArcanePattern extends Item {

    public ItemArcanePattern(Properties properties) {
        super(properties);
    }

    /**
     * Builds the item through AE2's own builder, so it is recognised as an encoded pattern.
     *
     * <p>{@code encodedPatternItemBuilder} is the supported door: it produces an {@code EncodedPatternItem},
     * the exact type {@code AEPatternDecoder.isEncodedPattern} tests for with {@code instanceof}. Extending
     * {@code Item} plainly would compile, register and tooltip correctly, and then fail the one check that
     * decides whether a crafting plan survives a save.
     */
    public static Item build() {
        return PatternDetailsHelper.encodedPatternItemBuilder(new Decoder()).build();
    }

    /**
     * Reads a pattern back out of its item form.
     *
     * <p><b>This is the method the old design was missing.</b> It takes the recipe the item carries, rebuilds
     * the pattern, and asks the assembler's own adapter for the AE2 view - the same call, on the same data,
     * that produced the pattern when it was first offered. So a decoded task is equal to a freshly offered one.
     */
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
            // The key itself is handed through as the definition, so the decoded task is equal to the one
            // this machine offers. Rebuilding it here would compare two independently serialised items and
            // depend on the pattern surviving save/load byte for byte - see ArcanePatternDetails.of.
            return ArcanePatternDetails.of(pattern, registries, null, key);
        }
    }
}
