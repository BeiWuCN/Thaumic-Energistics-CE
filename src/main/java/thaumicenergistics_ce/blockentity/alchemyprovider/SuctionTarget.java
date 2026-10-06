package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

/**
 * A neighbour that takes essentia because it wants some - a pipe, a Thaumatorium, a smelter - as
 * opposed to a container, which takes an insert. A suction machine offers no storage face at all,
 * so the container path cannot see one: that is how a provider beside a Thaumatorium came to
 * refuse every drop while showing no error. Essentia reaches one by being handed over instead.
 */
final class SuctionTarget {

    private final IEssentiaTransport transport;

    /** The machine's own face: an owner on the north side of it sits at its SOUTH face. */
    private final Direction face;

    private SuctionTarget(IEssentiaTransport transport, Direction face) {
        this.transport = transport;
        this.face = face;
    }

    /** The machine on that side of the owner, or null when that neighbour takes nothing from it. */
    static @Nullable SuctionTarget on(CachedEssentiaNeighbours neighbours, Direction side) {
        IEssentiaTransport transport = neighbours.transport(side);
        if (transport == null) {
            return null;
        }
        Direction face = side.getOpposite();
        return transport.isConnectable(face) ? new SuctionTarget(transport, face) : null;
    }

    /** The aspect the machine is asking for, or null while it has no work of its own. */
    @Nullable Holder<IAspect> wants() {
        if (transport.getSuctionAmount(face) <= 0) {
            return null;
        }
        return transport.getSuctionType(face);
    }

    /** Hands essentia over: the machine keeps what its current work needs and refuses the rest. */
    int accept(Holder<IAspect> aspect, int amount) {
        return transport.addEssentia(aspect, amount, face, false);
    }
}
