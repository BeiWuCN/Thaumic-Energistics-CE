package thaumicenergistics_ce.blockentity.gachabox;

import thaumicenergistics_ce.config.ThEConfig;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import net.minecraft.util.RandomSource;

public final class GachaOdds {

    public static final int MIN_SECONDS = 30;

    public static final int LONGEST_SECONDS = 180;

    public static final int SECONDS_PER_CARD = 25;

    public static final int LONGEST_WITH_CARDS = 80;

    public static final int MAX_CARDS = 4;

    public static final double CHANCE_AT_MIN_SECONDS = 0.75;

    public static final double CHANCE_AT_LONGEST_SECONDS = 0.45;

    public static final int MIN_DRAWS = 5;

    public static final int MAX_DRAWS = 7;

    public static final int MAX_POINTS_PER_DRAW = 3;

    public static final int COGNITIO_PER_TURN = 2;

    public static final double AE_PER_TURN = 500.0;

    public static int longestSeconds(int cards) {
        return Math.max(LONGEST_WITH_CARDS, LONGEST_SECONDS - SECONDS_PER_CARD * cards);
    }

    public static double payoutChance(int seconds) {
        double along = (double) (seconds - MIN_SECONDS) / (LONGEST_SECONDS - MIN_SECONDS);
        double shortest = ThEConfig.GACHA_SHORT_CHANCE.get();
        double longest = ThEConfig.GACHA_LONG_CHANCE.get();
        return shortest + (longest - shortest) * along;
    }

    public static Turn roll(RandomSource random, int cards) {
        int seconds = MIN_SECONDS + random.nextInt(longestSeconds(cards) - MIN_SECONDS + 1);
        return new Turn(seconds, random.nextDouble() < payoutChance(seconds));
    }

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

    private static KnowledgeType knowledgeType(RandomSource random) {
        return random.nextBoolean() ? KnowledgeType.THEORY : KnowledgeType.OBSERVATION;
    }

    public record Turn(int seconds, boolean pays) {}

    public record Payout(int observation, int theory) {

        public int points() {
            return observation + theory;
        }
    }

    private GachaOdds() {}
}
