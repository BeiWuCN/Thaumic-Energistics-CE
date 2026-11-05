package com.beiwu.thaumicenergistics_ce.blockentity.assembler;

/** Pure, side-effect-free payment planning. One Vis splits into one of each primal,
 * and one of each primal combines into one Vis. Recipe elements are reserved first. */
final class AssemblerVisPayment {
    record Plan(int auraVis, int[] remainingElements) {}

    private AssemblerVisPayment() {}

    static Plan plan(int[] stored, int availableAura, int baseVis, int[] required) {
        if (stored.length != 6 || required.length != 6 || baseVis < 0 || availableAura < 0) return null;
        for (int i = 0; i < 6; i++) {
            if (stored[i] < 0 || required[i] < 0) return null;
        }
        // Prefer chunk Vis for the plain-Vis bill, but allow a smaller chunk
        // allocation if splitting a Vis is necessary to cover a missing primal.
        for (int direct = Math.min(baseVis, availableAura); direct >= 0; direct--) {
            int sets = baseVis - direct;
            int split = 0;
            for (int i = 0; i < 6; i++) {
                int deficit = Math.max(0, required[i] - stored[i]);
                int afterRecipe = Math.max(0, stored[i] - required[i]);
                split = Math.max(split, deficit + Math.max(0, sets - afterRecipe));
            }
            if ((long) direct + split > availableAura) continue;
            int[] remaining = new int[6];
            for (int i = 0; i < 6; i++) {
                int deficit = Math.max(0, required[i] - stored[i]);
                int afterRecipe = Math.max(0, stored[i] - required[i]);
                int generatedForSets = split - deficit;
                remaining[i] = afterRecipe - Math.max(0, sets - generatedForSets);
            }
            return new Plan(direct + split, remaining);
        }
        return null;
    }
}
