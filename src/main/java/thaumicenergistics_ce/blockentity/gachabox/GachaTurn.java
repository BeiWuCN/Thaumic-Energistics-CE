package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

final class GachaTurn {

    private static final String TAG_SECONDS = "TurnSeconds";
    private static final String TAG_PAYS = "TurnPays";

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

    boolean earned() {
        return flashPaid;
    }

    void tickFlash() {
        flash--;
    }

    void start(GachaOdds.Turn drawn) {
        this.seconds = drawn.seconds();
        this.pays = drawn.pays();
        box.setChanged();
    }

    void advance() {
        seconds--;
        box.setChanged();
    }

    void settle() {
        this.flash = FLASH_SECONDS;
        this.flashPaid = this.pays;
        this.pays = false;
        box.setChanged();
    }

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
