package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.capability.KnowledgeType;
import net.minecraft.util.RandomSource;

/** 箱中一次转动背后的骰子：它转多久、是否给奖、给什么奖。
 * 这些数字是设计上的旋钮，集中放在这里，方块实体只需照办即可，而且
 * 时长与赔率是同一次掷骰，所以最短的转动也正是最可能给奖的那一次。 */
public final class GachaOdds {

    /** 最快的转动，也是赔率最好的那一次。 */
    public static final int MIN_SECONDS = 30;

    /** 箱中没有加速卡时最慢的转动：耐心档。 */
    public static final int LONGEST_SECONDS = 180;

    /** 每张加速卡把最长转动缩短多少。 */
    public static final int SECONDS_PER_CARD = 25;

    /** 加速卡最多把最长转动缩短到多少；第五张卡不再有任何改变。 */
    public static final int LONGEST_WITH_CARDS = 80;

    public static final int MAX_CARDS = 4;

    /** 赔率的两端：最短的转动给奖，最长的给奖更少。 */
    public static final double CHANCE_AT_MIN_SECONDS = 0.75;

    public static final double CHANCE_AT_LONGEST_SECONDS = 0.45;

    /** 一次给奖的转动做几次独立抽取；每次抽取带一至三点。 */
    public static final int MIN_DRAWS = 5;

    public static final int MAX_DRAWS = 7;

    /** 单次抽取最多可带的点数，也就是任何一次抽取最多交出多少。 */
    public static final int MAX_POINTS_PER_DRAW = 3;

    /** 一次转动消耗什么：原始点数很便宜，所以源质是代价中较小的那一半。 */
    public static final int COGNITIO_PER_TURN = 2;

    public static final double AE_PER_TURN = 500.0;

    /** 箱中装有这么多张卡时，箱子可能掷出的最长转动。 */
    public static int longestSeconds(int cards) {
        return Math.max(LONGEST_WITH_CARDS, LONGEST_SECONDS - SECONDS_PER_CARD * cards);
    }

    /** 这个时长的一次转动给奖的赔率，仅由时长算出。 */
    public static double payoutChance(int seconds) {
        double along = (double) (seconds - MIN_SECONDS) / (LONGEST_SECONDS - MIN_SECONDS);
        return CHANCE_AT_MIN_SECONDS + (CHANCE_AT_LONGEST_SECONDS - CHANCE_AT_MIN_SECONDS) * along;
    }

    /** 掷骰的两半共用一次抽取：落到的时长就决定这同一次转动的赔率。 */
    public static Turn roll(RandomSource random, int cards) {
        int seconds = MIN_SECONDS + random.nextInt(longestSeconds(cards) - MIN_SECONDS + 1);
        return new Turn(seconds, random.nextDouble() < payoutChance(seconds));
    }

    /** 一次给奖的转动交出什么：那些点数，按 Thaumaturge 的饰品划分它们的两种方式拆分。 */
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

    /** 理论或观察：一次转动交出的两类点数，每次抽取决定一个。 */
    private static KnowledgeType knowledgeType(RandomSource random) {
        return random.nextBoolean() ? KnowledgeType.THEORY : KnowledgeType.OBSERVATION;
    }

    /** 掷出的一次转动：它运行多少秒，以及它是否属于给奖的那些转动。 */
    public record Turn(int seconds, boolean pays) {}

    /** 一次给奖的转动交出什么，两类分开计数，好让每一类到达各自的要素。 */
    public record Payout(int observation, int theory) {

        /** 这次转动交出的全部点数，两类加在一起。 */
        public int points() {
            return observation + theory;
        }
    }

    private GachaOdds() {}
}
