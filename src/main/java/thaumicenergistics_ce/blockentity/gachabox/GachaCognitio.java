package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.nbt.CompoundTag;

/**
 * The cognitio banked toward the next turn. Nobody pays a turn in one go - a jar gives up a point a
 * call and a tube hands over one per call - so the reserve is what lets a run of them add up. It
 * never holds more than one turn costs, and what it holds is fuel: nothing is ever handed back out.
 */
final class GachaCognitio {

    private static final String TAG_COGNITIO = "Cognitio";

    private final BlockEntityGachaBox box;
    private int banked;

    GachaCognitio(BlockEntityGachaBox box) {
        this.box = box;
    }

    /** Whether the next turn is still short of its two points; only then does the box call. */
    boolean wants() {
        return banked < GachaOdds.COGNITIO_PER_TURN;
    }

    /** Whether a whole turn is covered, which is the point at which the box may start one. */
    boolean ready() {
        return !wants();
    }

    /** What the reserve could still take: at most what the next turn is short of. */
    int room() {
        return GachaOdds.COGNITIO_PER_TURN - banked;
    }

    /** Banks up to {@code amount} and answers how much of it was kept. */
    int add(int amount) {
        int taken = Math.min(amount, room());
        if (taken > 0) {
            banked += taken;
            box.setChanged();
        }
        return taken;
    }

    /** Spends one turn's worth, which the caller has already checked with {@link #ready()}. */
    boolean spend() {
        if (wants()) {
            return false;
        }
        banked -= GachaOdds.COGNITIO_PER_TURN;
        box.setChanged();
        return true;
    }

    void save(CompoundTag tag) {
        if (banked > 0) {
            tag.putInt(TAG_COGNITIO, banked);
        }
    }

    void load(CompoundTag tag) {
        banked = Math.max(0, Math.min(GachaOdds.COGNITIO_PER_TURN, tag.getInt(TAG_COGNITIO)));
    }
}
