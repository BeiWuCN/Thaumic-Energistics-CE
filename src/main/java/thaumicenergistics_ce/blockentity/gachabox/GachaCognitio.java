package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

final class GachaCognitio {

    private static final String TAG_COGNITIO = "Cognitio";

    private final BlockEntityGachaBox box;
    private int banked;

    GachaCognitio(BlockEntityGachaBox box) {
        this.box = box;
    }

    boolean wants() {
        return banked < GachaOdds.COGNITIO_PER_TURN;
    }

    boolean ready() {
        return !wants();
    }

    int room() {
        return GachaOdds.COGNITIO_PER_TURN - banked;
    }

    int add(int amount) {
        int taken = Math.min(amount, room());
        if (taken > 0) {
            banked += taken;
            box.setChanged();
        }
        return taken;
    }

    boolean spend() {
        if (wants()) {
            return false;
        }
        banked -= GachaOdds.COGNITIO_PER_TURN;
        box.setChanged();
        return true;
    }

    void save(ValueOutput output) {
        if (banked > 0) {
            output.putInt(TAG_COGNITIO, banked);
        }
    }

    void load(ValueInput input) {
        banked = Math.max(0, Math.min(GachaOdds.COGNITIO_PER_TURN, input.getIntOr(TAG_COGNITIO, 0)));
    }
}
