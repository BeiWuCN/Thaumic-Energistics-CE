package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.nbt.CompoundTag;

/**
 * 为下一次转动存入的 cognitio。
 * 一次转动不是一次付清的：一个罐每次调用放出一点，一根管道每次调用交过来一点，储备把一连串调用累加起来。
 * 持有的量不超一次转动的花费，而且只进不出，是燃料。
 */
final class GachaCognitio {

    private static final String TAG_COGNITIO = "Cognitio";

    private final BlockEntityGachaBox box;
    private int banked;

    GachaCognitio(BlockEntityGachaBox box) {
        this.box = box;
    }

    /** 下一次转动是不是还差它那两点；到那时箱子才索取。 */
    boolean wants() {
        return banked < GachaOdds.COGNITIO_PER_TURN;
    }

    /** 一整次转动是否已经凑齐；凑齐了箱子才能开始转。 */
    boolean ready() {
        return !wants();
    }

    /** 储备还能收多少：最多是下一次转动差的那点。 */
    int room() {
        return GachaOdds.COGNITIO_PER_TURN - banked;
    }

    /** 存入至多 {@code amount} 点，返回实际收下多少。 */
    int add(int amount) {
        int taken = Math.min(amount, room());
        if (taken > 0) {
            banked += taken;
            box.setChanged();
        }
        return taken;
    }

    /** 花掉一次转动的量；调用方先用 {@link #ready()} 查过。 */
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
