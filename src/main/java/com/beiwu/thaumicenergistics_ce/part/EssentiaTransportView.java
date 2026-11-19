package com.beiwu.thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.integration.ae2.essentia.ThEImmediateStorage;
import com.beiwu.thaumicenergistics_ce.util.ThETransaction;

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
    public @Nullable Holder<IAspect> getSuctionType(@Nullable Direction side) {
        return wantedAspect(side);
    }

    @Override
    public int getSuctionAmount(@Nullable Direction side) {
        if (side != null && side != face) {
            return 0;
        }
        Holder<IAspect> held = wantedAspect(side);
        if (held != null) {
            return acceptsMore(held) ? ACCEPTS_ONE_ASPECT : 0;
        }
        return storage.contents().isEmpty() ? ACCEPTS_ANY_ASPECT : 0;
    }

    @Override
    public int getMinimumSuction() {
        return ACCEPTS_ANY_ASPECT;
    }

    @Override
    public void setSuction(@Nullable Holder<IAspect> type, int count) {}

    private @Nullable Holder<IAspect> wantedAspect(@Nullable Direction side) {
        if (side != null && side != face) {
            return null;
        }
        for (var entry : storage.contents().sortedByAmount()) {
            if (entry.aspect() != null && entry.amount() > 0) {
                return entry.aspect();
            }
        }
        return null;
    }

    private boolean acceptsMore(Holder<IAspect> aspect) {
        if (storage instanceof ThEImmediateStorage immediate) {
            return immediate.previewInsert(aspect, PROBE) > 0;
        }
        return ThETransaction.preview(transaction -> storage.insert(aspect, PROBE, transaction)) > 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int count, Direction side) {
        if (count <= 0 || side != face) {
            return 0;
        }
        return ThETransaction.apply(transaction -> storage.insert(type, count, transaction));
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int count, Direction side, boolean simulate) {
        if (count <= 0 || side != face) {
            return 0;
        }
        return simulate
                ? storage instanceof ThEImmediateStorage immediate
                        ? immediate.previewInsert(type, count)
                        : ThETransaction.preview(transaction -> storage.insert(type, count, transaction))
                : ThETransaction.apply(transaction -> storage.insert(type, count, transaction));
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int count, Direction side) {
        if (count <= 0 || side != face) {
            return 0;
        }
        return ThETransaction.apply(transaction -> storage.extract(type, count, transaction));
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int count, Direction side, boolean simulate) {
        if (count <= 0 || side != face) {
            return 0;
        }
        return simulate
                ? storage instanceof ThEImmediateStorage immediate
                        ? immediate.previewExtract(type, count)
                        : ThETransaction.preview(transaction -> storage.extract(type, count, transaction))
                : ThETransaction.apply(transaction -> storage.extract(type, count, transaction));
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(@Nullable Direction side) {
        return wantedAspect(side);
    }

    @Override
    public int getEssentiaAmount(@Nullable Direction side) {
        Holder<IAspect> held = wantedAspect(side);
        return held == null ? 0 : storage.contents().amountOf(held);
    }
}
