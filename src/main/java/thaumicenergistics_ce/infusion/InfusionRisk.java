package thaumicenergistics_ce.infusion;

/**
 * How dangerous an infusion is: one number, and the tier of five that number falls in.
 *
 * <p>Thaumaturge gives an altar two things a player cannot see from outside. A recipe carries an
 * <b>instability</b> of its own - the more ambitious the ritual, the more it costs the altar's stability -
 * and the arrangement around the altar either helps or hurts: the stability survey walks the surroundings
 * and reports the blocks that break its symmetry. Both are the altar's business and neither is shown
 * anywhere a player standing next to it can read.
 *
 * <p>So this is the sum of the two, capped at {@link #CAP} - Thaumaturge's own ceiling on an altar,
 * {@code BlockEntityInfusionMatrix.STABILITY_CAP} - and then read as a tier, because a number between nought
 * and twenty-five tells a player nothing about what is going to happen to them. The tier bands and the
 * sentences that go with them are the reference build's, which is where the five-tier reading came from.
 *
 * <p><b>A shortage of essentia overrides the arithmetic.</b> An altar that cannot get what a ritual needs
 * does not simply wait: it grinds on with its stability draining, which is how a ritual that looked safe
 * turns into a wrecked pedestal. A shortage therefore lifts the tier to at least {@link #SHORTAGE_TIER}
 * whatever the numbers say.
 *
 * @param base the instability the recipe itself carries
 * @param altar what the altar's surroundings add, in blocks out of place
 * @param shortages whether the ritual is waiting for essentia it cannot reach
 * @param stability the altar's own stability right now, as it reports it - the number the goggles show
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
     *
     * <p><b>Read off the altar's own stability, not off the recipe.</b> The first version scored the recipe's
     * instability plus the blocks out of place, which is a property of the ritual and not of the altar - so
     * it did not move while the ritual ran, and a monitor that watched an altar go from 稳定 to 不稳定 said
     * the same number throughout. Reported as *"并不会跟着注魔祭坛的状态刷新"*.
     *
     * <p>The bands are Thaumaturge's own, from {@code BlockEntityInfusionMatrix}: 12.5 and 0 are the points
     * where its goggles change what they call the altar, and -25 is where it stops replenishing at all. The
     * five tiers split those four states at 20, so that a healthy altar has somewhere to be that is not
     * merely "the top band".
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
