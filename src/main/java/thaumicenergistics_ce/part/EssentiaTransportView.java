package thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.essentia.ThEImmediateStorage;
import thaumicenergistics_ce.util.ThETransaction;

final class EssentiaTransportView implements IEssentiaTransport {

    private static final int ACCEPTS_ANY_ASPECT = 32;

    private static final int ACCEPTS_ONE_ASPECT = 64;

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

    @Override
    public void setSuction(Holder<IAspect> aspect, int amount) {}

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

    @Override
    public int getSuctionAmount(Direction side) {
        Holder<IAspect> held = getSuctionType(side);
        if (held != null) {
            return acceptsMore(held) ? ACCEPTS_ONE_ASPECT : 0;
        }
        return storage.contents().isEmpty() ? ACCEPTS_ANY_ASPECT : 0;
    }

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
