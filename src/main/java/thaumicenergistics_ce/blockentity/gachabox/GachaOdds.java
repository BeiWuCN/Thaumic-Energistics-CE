package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import net.minecraft.util.RandomSource;

/** The dice behind one turn of the box: how long it turns, whether it pays out, and what it pays.
 * The numbers are the design's knobs, gathered here so the block entity only has to obey them, and
 * length and odds are one roll, so the shortest turn is also the one most likely to pay. */
public final class GachaOdds {

    /** Fastest turn, and the one with the best odds. */
    public static final int MIN_SECONDS = 30;

    /** Slowest turn with no speed cards in the box: the patient setting. */
    public static final int LONGEST_SECONDS = 180;

    /** How much shorter each speed card makes the longest turn. */
    public static final int SECONDS_PER_CARD = 25;

    /** What the cards shorten the longest turn to at most; a fifth card changes nothing. */
    public static final int LONGEST_WITH_CARDS = 80;

    public static final int MAX_CARDS = 4;

    /** The two ends of the odds: the shortest turn pays, the longest one pays less often. */
    public static final double CHANCE_AT_MIN_SECONDS = 0.75;

    public static final double CHANCE_AT_LONGEST_SECONDS = 0.45;

    /** How many separate draws a paying turn makes; each one carries one to three points. */
    public static final int MIN_DRAWS = 5;

    public static final int MAX_DRAWS = 7;

    /** Points a single draw may carry, and so the most any one draw hands over. */
    public static final int MAX_POINTS_PER_DRAW = 3;

    /** What one turn eats: raw points are cheap, so the essentia is the small half of the price. */
    public static final int COGNITIO_PER_TURN = 2;

    public static final double AE_PER_TURN = 500.0;

    /** The longest turn the box may roll with this many cards in it. */
    public static int longestSeconds(int cards) {
        return Math.max(LONGEST_WITH_CARDS, LONGEST_SECONDS - SECONDS_PER_CARD * cards);
    }

    /** The odds that a turn of this length pays out, worked out from the length alone. */
    public static double payoutChance(int seconds) {
        double along = (double) (seconds - MIN_SECONDS) / (LONGEST_SECONDS - MIN_SECONDS);
        return CHANCE_AT_MIN_SECONDS + (CHANCE_AT_LONGEST_SECONDS - CHANCE_AT_MIN_SECONDS) * along;
    }

    /** One draw for both halves of the roll: the length landed on sets the odds of this same turn. */
    public static Turn roll(RandomSource random, int cards) {
        int seconds = MIN_SECONDS + random.nextInt(longestSeconds(cards) - MIN_SECONDS + 1);
        return new Turn(seconds, random.nextDouble() < payoutChance(seconds));
    }

    /** What a paying turn hands out: the points, split the two ways Thaumaturge's curios split them. */
    public static Payout payout(RandomSource random) {
        int draws = MIN_DRAWS + random.nextInt(MAX_DRAWS - MIN_DRAWS + 1);
        int observation = 0;
        int theory = 0;
        for (int i = 0; i < draws; i++) {
            int points = 1 + random.nextInt(MAX_POINTS_PER_DRAW);
            if (knowledgeType(random) == KnowledgeType.THEORY) {
                theory += points;
            } else {
                observation += points;
            }
        }
        return new Payout(observation, theory);
    }

    /** Theory or observation: the two kinds of points a turn hands over, one draw at a time. */
    private static KnowledgeType knowledgeType(RandomSource random) {
        return random.nextBoolean() ? KnowledgeType.THEORY : KnowledgeType.OBSERVATION;
    }

    /** One turn as drawn: how many seconds it runs, and whether it is one of the turns that pays. */
    public record Turn(int seconds, boolean pays) {}

    /** What a paying turn hands over, counted apart so each kind reaches its own aspects. */
    public record Payout(int observation, int theory) {

        /** Every point this turn hands over, both kinds together. */
        public int points() {
            return observation + theory;
        }
    }

    private GachaOdds() {}
}
