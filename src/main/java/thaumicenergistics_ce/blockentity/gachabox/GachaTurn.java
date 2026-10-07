package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.nbt.CompoundTag;

/**
 * 进行中的转动：还剩多少秒、它是否属于给奖的那些转动，以及在下一次转动
 * 被掷出前把结果按在屏幕上的闪烁。箱子每秒 tick 它一次并决定屏幕显示
 * 什么；本类只负责让计数保持诚实。
 */
final class GachaTurn {

    private static final String TAG_SECONDS = "TurnSeconds";
    private static final String TAG_PAYS = "TurnPays";

    /** 结果在下一次转动被掷出前停留在屏幕上多久，以秒计：箱子每秒
     * 只被 tick 一次，所以这里数的是秒而不是 tick。 */
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

    /** 屏幕上的闪烁是一次给奖，还是一次什么都没抽到的转动。 */
    boolean earned() {
        return flashPaid;
    }

    void tickFlash() {
        flash--;
    }

    /** 开始一次掷出的转动；调用到这里时调用方已经为它付过账。 */
    void start(GachaOdds.Turn drawn) {
        this.seconds = drawn.seconds();
        this.pays = drawn.pays();
        box.setChanged();
    }

    /** 从进行中的转动上扣掉一秒。 */
    void advance() {
        seconds--;
        box.setChanged();
    }

    /** 把结束的转动放到屏幕上停留片刻，并将它移出进行中状态。 */
    void settle() {
        this.flash = FLASH_SECONDS;
        this.flashPaid = this.pays;
        this.pays = false;
        box.setChanged();
    }

    /** 没有进行中的转动，也没有在显示的东西：箱子刚被填装完时所处的状态。 */
    void reset() {
        this.seconds = 0;
        this.pays = false;
        this.flash = 0;
        this.flashPaid = false;
        box.setChanged();
    }

    void save(CompoundTag tag) {
        if (seconds > 0) {
            tag.putInt(TAG_SECONDS, seconds);
            tag.putBoolean(TAG_PAYS, pays);
        }
    }

    void load(CompoundTag tag) {
        this.seconds = Math.max(0, tag.getInt(TAG_SECONDS));
        this.pays = tag.getBoolean(TAG_PAYS);
    }
}
