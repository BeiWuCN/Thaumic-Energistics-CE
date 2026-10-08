package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * 进行中的转动：还剩几秒、是不是给奖的那一类，以及把结果压在屏幕上的闪烁。
 * 箱子每秒 tick 一次并决定显示什么，本类只管把计数数准。
 */
final class GachaTurn {

    private static final String TAG_SECONDS = "TurnSeconds";
    private static final String TAG_PAYS = "TurnPays";

    /** 结果在下一次转动掷出前压在屏幕上多少秒。
     * 箱子每秒只 tick 一次，这里数的是秒。 */
    private static final int FLASH_SECONDS = 2;

    private final BlockEntityGachaBox box;
    private int seconds;
    private boolean pays;
    private int flash;
    private boolean flashPaid;

    GachaTurn(BlockEntityGachaBox box) {
        this.box = box;
    }

    boolean running() {
        return seconds > 0;
    }

    boolean flashing() {
        return flash > 0;
    }

    /** 屏幕上的闪烁是给奖，还是什么都没抽到。 */
    boolean earned() {
        return flashPaid;
    }

    void tickFlash() {
        flash--;
    }

    /** 开始一次掷出的转动；调用到这里时账已经付过。 */
    void start(GachaOdds.Turn drawn) {
        this.seconds = drawn.seconds();
        this.pays = drawn.pays();
        box.setChanged();
    }

    void advance() {
        seconds--;
        box.setChanged();
    }

    /** 把结束的转动交给屏幕显示片刻，并退出进行中状态。 */
    void settle() {
        this.flash = FLASH_SECONDS;
        this.flashPaid = this.pays;
        this.pays = false;
        box.setChanged();
    }

    /** 进行中和显示都清空；箱子刚填装完时是这个状态。 */
    void reset() {
        this.seconds = 0;
        this.pays = false;
        this.flash = 0;
        this.flashPaid = false;
        box.setChanged();
    }

    void save(ValueOutput output) {
        if (seconds > 0) {
            output.putInt(TAG_SECONDS, seconds);
            output.putBoolean(TAG_PAYS, pays);
        }
    }

    void load(ValueInput input) {
        this.seconds = Math.max(0, input.getIntOr(TAG_SECONDS, 0));
        this.pays = input.getBooleanOr(TAG_PAYS, false);
    }
}
