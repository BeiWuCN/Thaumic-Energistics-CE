package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

final class SuctionTarget {

    private final IEssentiaTransport transport;

    private final Direction face;

    private SuctionTarget(IEssentiaTransport transport, Direction face) {
        this.transport = transport;
        this.face = face;
    }

    static @Nullable SuctionTarget on(CachedEssentiaNeighbours neighbours, Direction side) {
        IEssentiaTransport transport = neighbours.transport(side);
        if (transport == null) {
            return null;
        }
        Direction face = side.getOpposite();
        return transport.isConnectable(face) ? new SuctionTarget(transport, face) : null;
    }

    @Nullable Holder<IAspect> wants() {
        if (transport.getSuctionAmount(face) <= 0) {
            return null;
        }
        return transport.getSuctionType(face);
    }

    int accept(Holder<IAspect> aspect, int amount) {
        return transport.addEssentia(aspect, amount, face, false);
    }
}
