package thaumicenergistics_ce.blockentity.gachabox;

import net.minecraft.nbt.CompoundTag;

/**
 * 为下一次转动存入的 cognitio。没有人一次付清一次转动——一个罐每次调用
 * 放出一点，一根管道每次调用交过来一点——所以储备让一连串调用能累加起来。
 * 它持有的量从不超一次转动的花费，而它持有的东西是燃料：绝不会被交还出去。
 */
final class GachaCognitio {

    private static final String TAG_COGNITIO = "Cognitio";

    private final BlockEntityGachaBox box;
    private int banked;

    GachaCognitio(BlockEntityGachaBox box) {
        this.box = box;
    }

    /** 下一次转动是否还差它那两点；只有到那时箱子才会索取。 */
    boolean wants() {
        return banked < GachaOdds.COGNITIO_PER_TURN;
    }

    /** 一整次转动是否已被覆盖，这正是箱子可以开始一次转动的时点。 */
    boolean ready() {
        return !wants();
    }

    /** 储备还能接受多少：至多是下一次转动所差的量。 */
    int room() {
        return GachaOdds.COGNITIO_PER_TURN - banked;
    }

    /** 存入至多 {@code amount} 的点，并回答其中留下了多少。 */
    int add(int amount) {
        int taken = Math.min(amount, room());
        if (taken > 0) {
            banked += taken;
            box.setChanged();
        }
        return taken;
    }

    /** 花掉一次转动的量，调用方已经用 {@link #ready()} 检查过了。 */
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
