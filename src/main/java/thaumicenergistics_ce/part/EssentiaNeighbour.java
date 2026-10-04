package thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Finds the essentia container on the block a bus faces, and asks it the right question.
 * <ul>
 * <li>{@code isConnectable} takes the container's own face: a bus on the north side sits at its SOUTH face.
 * <li>Wrong face is silent: a jar answers true only at {@code UP}, so any other face inserts nothing.
 * <li>Order: transport on the bus's face, then storage on any face accepted, then a transport as storage.
 * </ul>
 */
final class EssentiaNeighbour {

    /** The six faces, so a container accepting several is tried in a stable order. */
    private static final Direction[] FACES = Direction.values();

    private EssentiaNeighbour() {}

    /**
     * The essentia container on {@code target}, reachable from the bus's own face, or null.
     *
     * @param from the direction from {@code target} back towards the bus - the container's face the bus is
     *     standing at. Callers pass {@code getSide().getOpposite()}.
     */
    static @Nullable IEssentiaStorage find(Level level, BlockPos target, Direction from) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }

        // Is anything here, and does it consider itself connected to us? A transport that says no does not
        // want a bus on this face, and an unconnected bus should be inactive rather than fail every tick.
        IEssentiaTransport transport = server.getCapability(EssentiaCapabilities.TRANSPORT, target, from);
        if (transport == null) {
            // No transport: a plain storage block with no opinion about faces, or nothing at all.
            return server.getCapability(EssentiaCapabilities.STORAGE, target, from);
        }
        if (!transport.isConnectable(from)) {
            return null;
        }

        // Prefer the storage view, on any face the container accepts: a jar taken this way is the same jar
        // whichever side the bus is on.
        for (Direction accepted : FACES) {
            if (!transport.isConnectable(accepted)) {
                continue;
            }
            IEssentiaStorage storage = server.getCapability(EssentiaCapabilities.STORAGE, target, accepted);
            if (storage != null) {
                return storage;
            }
        }

        // A pipe, or anything else that only speaks the transport protocol.
        return new Adapter(transport, from);
    }

    /**
     * Presents an {@link IEssentiaTransport} as an {@link IEssentiaStorage} on one face: everything here is
     * directional by necessity, since a pipe's contents differ per face and the adapter is built for one.
     */
    private record Adapter(IEssentiaTransport transport, Direction face) implements IEssentiaStorage {

        @Override
        public AspectList contents() {
            var aspect = transport.getEssentiaType(face);
            int amount = transport.getEssentiaAmount(face);
            return aspect == null || amount <= 0
                    ? AspectList.EMPTY
                    : AspectList.EMPTY.add(aspect, amount);
        }

        @Override
        public int amount(Holder<IAspect> aspect) {
            var held = transport.getEssentiaType(face);
            return held != null && held.equals(aspect) ? transport.getEssentiaAmount(face) : 0;
        }

        @Override
        public int insert(
                Holder<IAspect> aspect,
                int amount,
                boolean simulate) {
            return transport.addEssentia(aspect, amount, face, simulate);
        }

        @Override
        public int extract(
                Holder<IAspect> aspect,
                int amount,
                boolean simulate) {
            return transport.takeEssentia(aspect, amount, face, simulate);
        }

        @Override
        public long contentRevision() {
            // Nothing to offer, and 0 is honest: a caller caching on it simply refreshes.
            return 0;
        }
    }
}
