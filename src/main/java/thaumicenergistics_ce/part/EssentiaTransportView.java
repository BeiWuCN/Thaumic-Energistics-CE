package thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;

/**
 * Presents an essentia storage as the transport port a pipe expects at a given face.
 * <ul>
 * <li>A pipe asks a neighbour only for {@code EssentiaCapabilities.TRANSPORT}.
 * <li>Suction follows Thaumaturge's own jar, so a pipe may push into the bus or draw from it.
 * </ul>
 */
final class EssentiaTransportView implements IEssentiaTransport {

    /** What an unfiltered jar asks for, and the least a pipe must offer to be filled. */
    private static final int ACCEPTS_ANY_ASPECT = 32;

    /** A filter narrows a jar to one aspect, which is worth a stronger pull. */
    private static final int ACCEPTS_ONE_ASPECT = 64;

    /** Asked of the storage to find out whether it can still take more. One unit is the question. */
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

    /** A pipe sets suction on what it feeds; this port holds nothing of its own. */
    @Override
    public void setSuction(Holder<IAspect> aspect, int amount) {}

    /**
     * Wildcard: whatever the container holds, and - while it has room - whatever a pipe brings.
     * A face the bus is not on answers null, so a pipe reading round the block is told nothing is here.
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
     * Commits to being filled while the container has room, and zero once it has none, so a pipe stops
     * pushing and may pull instead. Asked as a simulation: the question is whether essence still fits.
     */
    @Override
    public int getSuctionAmount(Direction side) {
        Holder<IAspect> held = getSuctionType(side);
        if (held != null) {
            return storage.insert(held, PROBE, true) > 0 ? ACCEPTS_ONE_ASPECT : 0;
        }
        return storage.contents().isEmpty() ? ACCEPTS_ANY_ASPECT : 0;
    }

    @Override
    public int getMinimumSuction() {
        return ACCEPTS_ANY_ASPECT;
    }

    /** Refused outright rather than taken and dropped: a pipe must not lose essence to a full port. */
    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return canInputFrom(side) ? storage.insert(aspect, amount, false) : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction side) {
        return canOutputTo(side) ? storage.extract(aspect, amount, false) : 0;
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
