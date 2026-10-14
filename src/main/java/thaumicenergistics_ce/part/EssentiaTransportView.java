package thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaConduit;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.essentia.ThEImmediateStorage;
import thaumicenergistics_ce.util.ThETransaction;

final class EssentiaTransportView implements IEssentiaConduit {

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
    public boolean connectsOn(Direction side) {
        return side == face;
    }

    @Override
    public boolean receivesOn(Direction side) {
        return side == face;
    }

    @Override
    public boolean releasesOn(Direction side) {
        return side == face;
    }

    @Override
    public com.leclowndu93150.thaumaturge.api.essentia.EssentiaPull pull(Direction side) {
        return com.leclowndu93150.thaumaturge.api.essentia.EssentiaPull.of(wantedAspect(side), pullStrength(side));
    }

    private @Nullable Holder<IAspect> wantedAspect(Direction side) {
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

    private int pullStrength(Direction side) {
        if (side != null && side != face) {
            return 0;
        }
        Holder<IAspect> held = wantedAspect(side);
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
    public int pullThreshold() {
        return ACCEPTS_ANY_ASPECT;
    }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, Direction side, boolean simulate) {
        if (amount <= 0 || !receivesOn(side)) {
            return 0;
        }
        return simulate ? storage instanceof ThEImmediateStorage immediate ? immediate.previewInsert(aspect, amount)
                : ThETransaction.preview(transaction -> storage.insert(aspect, amount, transaction))
                : ThETransaction.apply(transaction -> storage.insert(aspect, amount, transaction));
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, Direction side, boolean simulate) {
        if (amount <= 0 || !releasesOn(side)) {
            return 0;
        }
        return simulate ? storage instanceof ThEImmediateStorage immediate ? immediate.previewExtract(aspect, amount)
                : ThETransaction.preview(transaction -> storage.extract(aspect, amount, transaction))
                : ThETransaction.apply(transaction -> storage.extract(aspect, amount, transaction));
    }

    @Override
    public @Nullable Holder<IAspect> heldAspect(Direction side) {
        return wantedAspect(side);
    }

    @Override
    public int heldAmount(Direction side) {
        Holder<IAspect> held = wantedAspect(side);
        return held == null ? 0 : storage.contents().amountOf(held);
    }
}
