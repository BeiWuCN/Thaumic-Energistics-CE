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
 * 缓冲是一个计数而不是要素列表，所以保留的要素只用于显示；每次变化都落在
 * 这里，因此随之递增的 revision 就是缓存唯一需要的答案。管道所见——空间、
 * 吸力、内容——都从这个槽位读出，绝不从燃烧读出。
 */
final class ChamberEssentiaTank {

    private static final int SUCTION = 128;

    private final BlockEntityEssentiaVibrationChamber chamber;

    private final CachedEssentiaNeighbours neighbours;

    private int storedEssentia;

    private @Nullable Holder<IAspect> currentAspect;

    /** 缓冲每次变化都递增，好让本容器内容的缓存察觉。 */
    private long revision;

    /** 追踪自上一行以来看到到达的量，以便区分根本送不到的管道。 */
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

    /** 缓冲或能量槽满时返回 0：管道靠这个数字导向，而一台满的机器若宣称
     * 有吸力，会吸来它烧不掉的源质。 */
    int suctionAmount(boolean paused) {
        return hasRoom() && !paused ? SUCTION : 0;
    }

    /** 整个缓冲都记在最后放入的要素名下：缓冲是一个计数，所以这个答案是有损的。 */
    AspectList contents() {
        if (storedEssentia <= 0 || currentAspect == null) {
            return AspectList.EMPTY;
        }
        return AspectList.EMPTY.add(currentAspect, storedEssentia);
    }

    /** 所持要素的 id，没有持有任何东西时为 null。 */
    @Nullable ResourceLocation aspectId() {
        return currentAspect == null
                ? null
                : currentAspect.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    /** 所持要素的 path，没有持有任何东西时为空；燃烧的定价取决于它。 */
    String aspectPath() {
        return currentAspect == null
                ? ""
                : currentAspect.unwrapKey().map(key -> key.location().getPath()).orElse("");
    }

    /**
     * 从相邻容器抽取一份：没有“能装多少抽多少”这种调用，而把多余的退回去
     * 正是源质丢失的地方。
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
     * 从 Thaumaturge 的源质管道抽取，它不会向途经的机器推送：目的地是主动
     * 询问的那一侧，与 Thaumaturge 的端口一致。下面的判断条件就是那个端口的。
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

    /** 按给出的要素取走装得下的量；缓冲按最后放入的要素来读取。 */
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
        // tooltip 在客户端读取缓冲；燃料是一次一份到达，而不是每 tick 都到。
        chamber.markForClientUpdate();
    }

    /** 一次填充会取走多少，已封顶，且仅在非模拟时才真正应用。 */
    int insert(Holder<IAspect> aspect, int amount, boolean simulate, boolean paused) {
        // 与抽取和吸力同一条规则：满的槽位什么都不收，无论以何种方式提供。
        if (aspect == null || amount <= 0 || paused) {
            return 0;
        }
        // 下限为 0：保存的计数若超过上限会算出负数，被读成“什么都没取”。
        int accepted = Math.min(amount, space());
        if (accepted > 0 && !simulate) {
            accept(aspect, accepted);
        }
        return accepted;
    }

    /** 交回一份燃料，与其它任何变化一样计为内容变化。 */
    void revertOne() {
        storedEssentia--;
        revision++;
    }

    /** 按给定值放入计数和要素，无论它们来自线上还是来自保存的标签。 */
    void set(int essentia, @Nullable Holder<IAspect> aspect) {
        storedEssentia = essentia;
        currentAspect = aspect;
    }
}
