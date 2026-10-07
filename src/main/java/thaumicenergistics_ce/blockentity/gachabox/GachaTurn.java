package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.nbt.CompoundTag;

/**
 * The turn in flight: how many seconds are left of it, whether it is one of the turns that pays,
 * and the flash that holds its result on the screen before the next one is drawn. The box ticks it
 * one second at a time and decides what the screen shows; this class only keeps the count honest.
 */
final class GachaTurn {

    private static final String TAG_SECONDS = "TurnSeconds";
    private static final String TAG_PAYS = "TurnPays";

    /** How long a result stays on the screen before the next turn is drawn, in seconds: the box is
     * ticked once a second, so this counts seconds and not ticks. */
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

    /** Whether the flash on the screen is a payout or a turn that drew nothing. */
    boolean earned() {
        return flashPaid;
    }

    void tickFlash() {
        flash--;
    }

    /** Starts a drawn turn; the caller has already paid for it by the time this is called. */
    void start(GachaOdds.Turn drawn) {
        this.seconds = drawn.seconds();
        this.pays = drawn.pays();
        box.setChanged();
    }

    /** Counts one second off the turn in flight. */
    void advance() {
        seconds--;
        box.setChanged();
    }

    /** Puts the finished turn on the screen for a moment and takes it out of flight. */
    void settle() {
        this.flash = FLASH_SECONDS;
        this.flashPaid = this.pays;
        this.pays = false;
        box.setChanged();
    }

    /** Nothing in flight and nothing showing: the state a box is in when it has just been filled. */
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
