package thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.essentia.ThEImmediateStorage;
import thaumicenergistics_ce.util.ThETransaction;

/**
 * 把源质存储呈现为管道在某一面期望的传输端口。
 * <ul>
 * <li>管道只向邻居索取 {@code EssentiaCapabilities.TRANSPORT}。
 * <li>吸力照抄 Thaumaturge 自己的罐子，管道既可以推进总线，也可以从总线抽取。
 * </ul>
 */
final class EssentiaTransportView implements IEssentiaTransport {

    /** 未过滤的罐子所求的量，也是管道要被填满至少要给的量。 */
    private static final int ACCEPTS_ANY_ASPECT = 32;

    /** 过滤把罐子收窄到一种要素，值得更强的抽力。 */
    private static final int ACCEPTS_ONE_ASPECT = 64;

    /** 问存储还能不能再装。一次问的就是一个单位。 */
    private static final int PROBE = 1;

    private final IEssentiaStorage storage;

    private final Direction face;

    EssentiaTransportView(IEssentiaStorage storage, Direction face) {
        this.storage = storage;
        this.face = face;
    }

    @Override
    public boolean isConnectable(Direction side) {
        return side == face;
    }

    @Override
    public boolean canInputFrom(Direction side) {
        return side == face;
    }

    @Override
    public boolean canOutputTo(Direction side) {
        return side == face;
    }

    /** 管道给它所喂的对象设吸力；这个端口自身什么都不持有。 */
    @Override
    public void setSuction(Holder<IAspect> aspect, int amount) {}

    /**
     * 通配：容器里有什么就是什么；容器还有空位时，管道送来的也照收。
     * 总线不在的那一面答 null，管道绕到别面去读只会被告知这里什么都没有。
     */
    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction side) {
        if (side != face) {
            return null;
        }
        for (var entry : storage.contents().sortedByAmount()) {
            if (entry.aspect() != null && entry.amount() > 0) {
                return entry.aspect();
            }
        }
        return null;
    }

    /**
     * 容器还有空位就答应被填，没空位就是零，管道于是停止推送、改为抽取。
     * 这只是预览：问的是源质还装不装得下。
     */
    @Override
    public int getSuctionAmount(Direction side) {
        Holder<IAspect> held = getSuctionType(side);
        if (held != null) {
            return acceptsMore(held) ? ACCEPTS_ONE_ASPECT : 0;
        }
        return storage.contents().isEmpty() ? ACCEPTS_ANY_ASPECT : 0;
    }

    /**
     * 再装一个单位还装不装得下。管道那一面直接问，因为它给什么就用什么，
     * 没有事务能把那一步收回。
     */
    private boolean acceptsMore(Holder<IAspect> aspect) {
        if (storage instanceof ThEImmediateStorage immediate) {
            return immediate.previewInsert(aspect, PROBE) > 0;
        }
        return ThETransaction.preview(transaction -> storage.insert(aspect, PROBE, transaction)) > 0;
    }

    @Override
    public int getMinimumSuction() {
        return ACCEPTS_ANY_ASPECT;
    }

    /** 直接拒绝，而不是收下再丢掉：管道不能把源质丢给一个满了的端口。 */
    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        if (!canInputFrom(side)) {
            return 0;
        }
        return ThETransaction.apply(transaction -> storage.insert(aspect, amount, transaction));
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        if (!canOutputTo(side)) {
            return 0;
        }
        return ThETransaction.apply(transaction -> storage.extract(aspect, amount, transaction));
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction side) {
        return getSuctionType(side);
    }

    @Override
    public int getEssentiaAmount(Direction side) {
        Holder<IAspect> held = getSuctionType(side);
        return held == null ? 0 : storage.contents().amountOf(held);
    }
}
