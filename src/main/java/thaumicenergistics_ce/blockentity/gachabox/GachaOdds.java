package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import net.minecraft.util.RandomSource;

/** 箱里一次转动背后的骰子：转多久、给不给奖、给什么奖。
 * 数字都集中在这里，方块实体照办。
 * 时长和赔率来自同一次掷骰：最短的转动就是最可能给奖的那一次。 */
public final class GachaOdds {

    /** 最快的一次转动，也是赔率最好的一次。 */
    public static final int MIN_SECONDS = 30;

    /** 没有加速卡时最慢的转动，180 秒。 */
    public static final int LONGEST_SECONDS = 180;

    /** 每张加速卡把最长转动缩短 25 秒。 */
    public static final int SECONDS_PER_CARD = 25;

    /** 加速卡最多把最长转动压到 80 秒；第五张卡起不再变化。 */
    public static final int LONGEST_WITH_CARDS = 80;

    public static final int MAX_CARDS = 4;

    /** 赔率的高端 0.75：最短的转动给奖，最长的那档更低。 */
    public static final double CHANCE_AT_MIN_SECONDS = 0.75;

    public static final double CHANCE_AT_LONGEST_SECONDS = 0.45;

    /** 给奖的转动抽 5 次，每次带 1 到 3 点。 */
    public static final int MIN_DRAWS = 5;

    public static final int MAX_DRAWS = 7;

    public static final int MAX_POINTS_PER_DRAW = 3;

    /** 一次转动扣 2 点 cognitio；原始点数便宜，源质才是代价里大的那半。 */
    public static final int COGNITIO_PER_TURN = 2;

    public static final double AE_PER_TURN = 500.0;

    /** 装这么多张卡时可能掷出的最长转动。 */
    public static int longestSeconds(int cards) {
        return Math.max(LONGEST_WITH_CARDS, LONGEST_SECONDS - SECONDS_PER_CARD * cards);
    }

    /** 这个时长的转动给奖的赔率，只看时长。 */
    public static double payoutChance(int seconds) {
        double along = (double) (seconds - MIN_SECONDS) / (LONGEST_SECONDS - MIN_SECONDS);
        return CHANCE_AT_MIN_SECONDS + (CHANCE_AT_LONGEST_SECONDS - CHANCE_AT_MIN_SECONDS) * along;
    }

    /** 一次抽取定下两半：落到的时长决定这次转动的赔率。 */
    public static Turn roll(RandomSource random, int cards) {
        int seconds = MIN_SECONDS + random.nextInt(longestSeconds(cards) - MIN_SECONDS + 1);
        return new Turn(seconds, random.nextDouble() < payoutChance(seconds));
    }

    /** 给奖的转动交出的点数，按 Thaumaturge 的两种划分拆开。 */
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

    /** 理论还是观察：每次抽取定一类。 */
    private static KnowledgeType knowledgeType(RandomSource random) {
        return random.nextBoolean() ? KnowledgeType.THEORY : KnowledgeType.OBSERVATION;
    }

    /** 掷出的一次转动：跑多少秒，给不给奖。 */
    public record Turn(int seconds, boolean pays) {}

    /** 给奖的转动交出的东西，两类分开计数，各自进对应的要素。 */
    public record Payout(int observation, int theory) {

        /** 这次转动的总点数，两类相加。 */
        public int points() {
            return observation + theory;
        }
    }

    private GachaOdds() {}
}
