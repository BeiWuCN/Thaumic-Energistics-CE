package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaConduit;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.registry.capability.CachedEssentiaNeighbours;

final class SuctionTarget {

    private final IEssentiaConduit transport;

    private final Direction face;

    private SuctionTarget(IEssentiaConduit transport, Direction face) {
        this.transport = transport;
        this.face = face;
    }

    static @Nullable SuctionTarget on(CachedEssentiaNeighbours neighbours, Direction side) {
        IEssentiaConduit transport = neighbours.transport(side);
        if (transport == null) {
            return null;
        }
        Direction face = side.getOpposite();
        return transport.connectsOn(face) ? new SuctionTarget(transport, face) : null;
    }

    @Nullable Holder<IAspect> wants() {
        if (transport.pull(face).strength() <= 0) {
            return null;
        }
        return transport.pull(face).aspect();
    }

    int accept(Holder<IAspect> aspect, int amount) {
        return transport.insert(aspect, amount, face, false);
    }
}
