package thaumicenergistics_ce.blockentity.vibrationchamber;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

/**
 * 振动室的燃料槽：以最后放入的要素计数的源质量，以及把它填满的抽取。
 * 缓冲是个计数，不是要素列表，留住要素只为显示；每次变化都落在这里，
 * 跟着递增的 revision 就是缓存唯一的依据。
 * 管道看到的空间、吸力、内容都从这个槽位读出，不从燃烧读。
 */
final class ChamberEssentiaTank {

    private static final int SUCTION = 128;

    private final BlockEntityEssentiaVibrationChamber chamber;

    private final CachedEssentiaNeighbours neighbours;

    private int storedEssentia;

    private @Nullable Holder<IAspect> currentAspect;

    /** 缓冲每次变化都加一，容器内容的缓存靠它察觉。 */
    private long revision;

    /** 记下自上次记录以来到达的量，好区分根本送不到的管道。 */
    private int tracedEssentia;

    ChamberEssentiaTank(BlockEntityEssentiaVibrationChamber chamber) {
        this.chamber = chamber;
        this.neighbours = new CachedEssentiaNeighbours(chamber);
    }

    int amount() {
        return storedEssentia;
    }

    @Nullable Holder<IAspect> aspect() {
        return currentAspect;
    }

    long revision() {
        return revision;
    }

    int traced() {
        return tracedEssentia;
    }

    void resetTraced() {
        tracedEssentia = 0;
    }

    boolean hasRoom() {
        return storedEssentia < BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA;
    }

    int space() {
        return Math.max(0, BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA - storedEssentia);
    }

    /** 缓冲或能量槽满就返回 0：管道按这个数字导向，
     * 满的机器若还宣称有吸力，会吸来它烧不掉的源质。 */
    int suctionAmount(boolean paused) {
        return hasRoom() && !paused ? SUCTION : 0;
    }

    /** 整个缓冲都记在最后放入的要素名下：缓冲是个计数，这个答案是有损的。 */
    AspectList contents() {
        if (storedEssentia <= 0 || currentAspect == null) {
            return AspectList.EMPTY;
        }
        return AspectList.EMPTY.add(currentAspect, storedEssentia);
    }

    /** 持有的要素 id，空的时候为 null。 */
    @Nullable ResourceLocation aspectId() {
        return currentAspect == null
                ? null
                : currentAspect.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    /** 持有的要素 path，空的时候是空串；燃烧的定价看它。 */
    String aspectPath() {
        return currentAspect == null
                ? ""
                : currentAspect.unwrapKey().map(key -> key.location().getPath()).orElse("");
    }

    /**
     * 从相邻容器抽一份：没有「能装多少抽多少」的调用，
     * 多抽的部分退回去就会丢源质。
     */
    void pull() {
        if (chamber.getLevel() == null || !hasRoom()) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (pullFromContainer(side) || pullFromTube(side)) {
                return;
            }
        }
    }

    private boolean pullFromContainer(Direction side) {
        IEssentiaStorage storage = neighbours.storage(side);
        if (storage == null) {
            return false;
        }
        for (AspectInstance entry : storage.contents().entries()) {
            Holder<IAspect> aspect = entry.aspect();
            if (entry.amount() <= 0) {
                continue;
            }
            int taken = storage.extract(aspect, 1, false);
            if (taken > 0) {
                accept(aspect, taken);
                return true;
            }
        }
        return false;
    }

    /**
     * 从 Thaumaturge 的源质管道抽，它不向途经的机器推送：
     * 目的地是主动询问的那一侧，和 Thaumaturge 的端口一致，下面的判断就是那个端口的。
     */
    private boolean pullFromTube(Direction side) {
        Direction facing = side.getOpposite();
        IEssentiaTransport tube = neighbours.transport(side);
        if (tube == null || !tube.canOutputTo(facing)) {
            return false;
        }
        if (tube.getEssentiaAmount(facing) <= 0
                || tube.getSuctionAmount(facing) >= chamber.getSuctionAmount(side)
                || chamber.getSuctionAmount(side) < tube.getMinimumSuction()) {
            return false;
        }
        Holder<IAspect> aspect = tube.getEssentiaType(facing);
        if (aspect == null) {
            return false;
        }
        int taken = tube.takeEssentia(aspect, 1, facing);
        if (taken > 0) {
            accept(aspect, taken);
            return true;
        }
        return false;
    }

    /** 按给定要素取走装得下的量；缓冲仍按最后放入的要素读。 */
    void accept(Holder<IAspect> aspect, int amount) {
        int taken = Math.min(amount, space());
        if (taken <= 0) {
            return;
        }
        storedEssentia += taken;
        tracedEssentia += taken;
        currentAspect = aspect;
        revision++;
        chamber.setChanged();
        // tooltip 在客户端读缓冲；燃料一份一份到，不每 tick 都有。
        chamber.markForClientUpdate();
    }

    /** 一次填充能取走多少，已封顶；只有非模拟时才真正落账。 */
    int insert(Holder<IAspect> aspect, int amount, boolean simulate, boolean paused) {
        // 和抽取、吸力同一条规则：满槽什么都不收，不管从哪条路径来。
        if (aspect == null || amount <= 0 || paused) {
            return 0;
        }
        // 下限取 0：保存的计数超过上限会算出负数，被读成「什么都没取」。
        int accepted = Math.min(amount, space());
        if (accepted > 0 && !simulate) {
            accept(aspect, accepted);
        }
        return accepted;
    }

    /** 退回一份燃料，跟别的内容变化一样算变化。 */
    void revertOne() {
        storedEssentia--;
        revision++;
    }

    /** 按给定值写入计数和要素，不管来自网络还是保存的标签。 */
    void set(int essentia, @Nullable Holder<IAspect> aspect) {
        storedEssentia = essentia;
        currentAspect = aspect;
    }
}
