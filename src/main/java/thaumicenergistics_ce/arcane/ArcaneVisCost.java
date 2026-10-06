package thaumicenergistics_ce.arcane;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;

/**
 * The vis price of an arcane pattern: the wand's base cost plus the primal crystals it substitutes.
 * A compound crystal counts as its primals, which Thaumaturge does not substitute.
 */
final class ArcaneVisCost {

    private ArcaneVisCost() {}

    static int crystalVis(AspectList crystals) {
        return primalCrystals(crystals).totalAmount() * ThEArcanePattern.CRYSTAL_SUBSTITUTE_VIS;
    }

    static int totalVis(int baseVis, AspectList crystals) {
        return Math.max(0, baseVis) + crystalVis(crystals);
    }

    static int chargedVis(int baseVis, AspectList crystals) {
        float modifier = crystals.entries().isEmpty() ? 1.0F : ThEArcanePattern.CRAFT_AURA_SURCHARGE;
        return (int) Math.ceil(totalVis(baseVis, crystals) * modifier);
    }

    static AspectList primalCrystals(AspectList crystals) {
        return filter(crystals, true);
    }

    static AspectList nonPrimalCrystals(AspectList crystals) {
        return filter(crystals, false);
    }

    private static AspectList filter(AspectList crystals, boolean primal) {
        AspectList filtered = AspectList.EMPTY;
        for (AspectInstance entry : crystals.entries()) {
            if (entry.aspect().value().isPrimal() == primal) {
                filtered = filtered.add(entry.aspect(), entry.amount());
            }
        }
        return filtered;
    }
}
