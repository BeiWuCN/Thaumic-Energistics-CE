package thaumicenergistics_ce.infusion;

/**
 * How dangerous an infusion is: one number between nought and {@link #CAP}, and the tier of five it
 * falls in, because that number alone tells a player nothing about what is going to happen to them.
 * <ul>
 * <li>The number is the recipe's own instability plus what the surroundings cost: the stability
 * survey walks the room and reports the blocks breaking its symmetry, and neither figure is
 * readable from outside.
 * <li>{@link #CAP} is Thaumaturge's ceiling on an altar
 * ({@code BlockEntityInfusionMatrix.STABILITY_CAP}).
 * <li>A shortage of essentia lifts the tier to at least {@link #SHORTAGE_TIER} whatever the numbers
 * say: an altar that cannot get what a ritual needs does not wait, it grinds on with its stability
 * draining, which is how a ritual that looked safe turns into a wrecked pedestal.
 * <li>The bands and their sentences are the reference build's, which is where the
 * five-tier reading came from.
 * </ul>
 *
 * @param base the instability the recipe itself carries
 * @param altar what the altar's surroundings add, in blocks out of place
 * @param shortages whether the ritual is waiting for essentia it cannot reach
 * @param stability the altar's own stability right now, as it reports it - what the goggles show
 */
public record InfusionRisk(int base, int altar, boolean shortages, float stability) {

    public static final InfusionRisk NONE = new InfusionRisk(0, 0, false, 25.0F);

    /** Thaumaturge's ceiling on an altar's instability. See the class note. */
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
     * Which of the five tiers this risk is, from 1 (barely anything) to {@link #MAX_TIER}.
     * <ul>
     * <li>Read off the altar's own stability, not the recipe: scoring the recipe instead did not move
     * while the ritual ran, so a monitor watching an altar go from 稳定 to 不稳定 reported one number
     * throughout, filed as *"并不会跟着注魔祭坛的状态刷新"*.
     * <li>The bands are Thaumaturge's own ({@code BlockEntityInfusionMatrix}): 12.5 and 0 are where
     * its goggles change what they call the altar, -25 where it stops replenishing. The five tiers
     * split those four states at 20, so a healthy altar has somewhere to be that is not merely
     * "the top band".
     * </ul>
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
