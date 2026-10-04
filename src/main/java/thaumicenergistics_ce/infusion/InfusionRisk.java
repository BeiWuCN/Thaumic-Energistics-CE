package thaumicenergistics_ce.infusion;

/**
 * How dangerous an infusion is: one number between nought and {@link #CAP}, and the tier of five it falls in.
 *
 * @param base the instability the recipe itself carries
 * @param altar what the altar's surroundings add, in blocks out of place
 * @param shortages whether the ritual is waiting for essentia it cannot reach
 * @param stability the altar's own stability right now, as it reports it - what the goggles show
 */
public record InfusionRisk(int base, int altar, boolean shortages, float stability) {

    public static final InfusionRisk NONE = new InfusionRisk(0, 0, false, 25.0F);

    /** Thaumaturge's ceiling on an altar's instability ({@code BlockEntityInfusionMatrix.STABILITY_CAP}). */
    public static final int CAP = 25;

    /** The lowest tier a ritual with an essentia shortage is reported at, however calm the numbers look. */
    public static final int SHORTAGE_TIER = 4;

    /** The highest tier, so callers can size a scale without knowing the bands. */
    public static final int MAX_TIER = 5;

    /** The instability, as the altar would count it: what the recipe costs plus what the room costs. */
    public int instability() {
        return Math.min(CAP, Math.max(0, base) + Math.max(0, altar));
    }

    /**
     * Which of the five tiers this risk is, from 1 (barely anything) to {@link #MAX_TIER}: read off the
     * altar's stability at Thaumaturge's own thresholds (12.5, 0, -25), split into five at 20.
     */
    public int tier() {
        int tier;
        if (stability > 20.0F) {
            tier = 1;
        } else if (stability > 12.5F) {
            tier = 2;
        } else if (stability >= 0.0F) {
            tier = 3;
        } else if (stability > -25.0F) {
            tier = 4;
        } else {
            tier = 5;
        }
        return shortages ? Math.max(tier, SHORTAGE_TIER) : tier;
    }
}
