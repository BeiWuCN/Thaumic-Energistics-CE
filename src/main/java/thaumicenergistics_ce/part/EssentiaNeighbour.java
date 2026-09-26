package thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Finding the essentia container on the block a bus faces, and asking it the right question.
 *
 * <h2>The direction is the bit that is easy to get wrong</h2>
 *
 * <p>A Thaumaturge essentia container exposes two capabilities, {@link EssentiaCapabilities#TRANSPORT} and
 * {@link EssentiaCapabilities#STORAGE}, and they answer two different questions:
 *
 * <ul>
 *   <li>{@link IEssentiaTransport#isConnectable(Direction)} takes <b>the container's own face</b> - the one
 *       the caller is standing at. A bus on the north face of a block is at that block's {@code SOUTH} face,
 *       so the question is {@code isConnectable(SOUTH)}.
 *   <li>The {@code Direction} passed to a block capability lookup is also a face of the <em>target</em>, and
 *       it is the same face: the one the query comes from.
 * </ul>
 *
 * <p>Getting either wrong is silent. A jar answers {@code isConnectable} true for {@code UP} and false for
 * everything else - it is filled from the top - so the first version of this, which resolved the capability
 * and then inserted without asking, sent every transfer to a face the jar does not accept.
 *
 * <h2>Two capabilities, and why the storage one is preferred</h2>
 *
 * <p>{@code STORAGE} is the richer interface and the one to use when it is offered, but it is a capability
 * lookup and a lookup is always directional. Asking for it on the face the bus is on gets nothing when the
 * container's accepted face is a different one.
 *
 * <p>So the order is:
 *
 * <ol>
 *   <li>the transport capability on the face the bus is on, to decide whether this is a connection at all;
 *   <li>the storage capability on each face the container <em>accepts</em>, in the order it accepts them.
 *       This is what lets a bus on a jar's side use the jar: the jar accepts {@code UP}, and its storage
 *       view for {@code UP} works from anywhere;
 *   <li>failing that, the transport capability wrapped as storage, which is how a pipe is attached to - a
 *       pipe exposes no storage at all.
 * </ol>
 *
 * <p>The {@link Adapter} is what makes one interface out of the two: an {@code IEssentiaTransport} presented
 * as an {@link IEssentiaStorage} whose {@code insert} and {@code extract} are {@code addEssentia} and
 * {@code takeEssentia} on one face, and whose {@code contents()} is what the container admits to holding
 * there.
 */
final class EssentiaNeighbour {

    /** The six faces, so a container that accepts several is tried in a stable order. */
    private static final Direction[] FACES = Direction.values();

    private EssentiaNeighbour() {}

    /**
     * The essentia container on {@code target}, reachable from the face the bus occupies, or null.
     *
     * @param from the direction from {@code target} back towards the bus - the container's own face that the
     *     bus is standing at. Callers pass {@code getSide().getOpposite()}.
     */
    static @Nullable IEssentiaStorage find(Level level, BlockPos target, Direction from) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }

        // Is anything here, and does it consider itself connected to us? A transport that says no is a
        // container that does not want a bus on this face, and a bus that is not connected should do nothing
        // and show as inactive rather than quietly failing a transfer every tick.
        IEssentiaTransport transport = server.getCapability(EssentiaCapabilities.TRANSPORT, target, from);
        if (transport == null) {
            // No transport: a plain storage block with no opinion about faces, or nothing at all.
            return server.getCapability(EssentiaCapabilities.STORAGE, target, from);
        }
        if (!transport.isConnectable(from)) {
            return null;
        }

        // Prefer the storage view, on any face the container accepts. A jar taken this way is the same jar
        // whichever side the bus is on, which is what a player expects of a container.
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
     * Presents an {@link IEssentiaTransport} as an {@link IEssentiaStorage} on one face.
     *
     * <p>Everything here is directional, which is the point: a pipe's contents differ per face, so the
     * adapter is built for a face rather than for the block.
     */
    private record Adapter(IEssentiaTransport transport, Direction face) implements IEssentiaStorage {

        @Override
        public com.leclowndu93150.thaumaturge.api.aspect.AspectList contents() {
            var aspect = transport.getEssentiaType(face);
            int amount = transport.getEssentiaAmount(face);
            return aspect == null || amount <= 0
                    ? com.leclowndu93150.thaumaturge.api.aspect.AspectList.EMPTY
                    : com.leclowndu93150.thaumaturge.api.aspect.AspectList.EMPTY.add(aspect, amount);
        }

        @Override
        public int amount(net.minecraft.core.Holder<com.leclowndu93150.thaumaturge.api.aspect.IAspect> aspect) {
            var held = transport.getEssentiaType(face);
            return held != null && held.equals(aspect) ? transport.getEssentiaAmount(face) : 0;
        }

        @Override
        public int insert(
                net.minecraft.core.Holder<com.leclowndu93150.thaumaturge.api.aspect.IAspect> aspect,
                int amount,
                boolean simulate) {
            return transport.addEssentia(aspect, amount, face, simulate);
        }

        @Override
        public int extract(
                net.minecraft.core.Holder<com.leclowndu93150.thaumaturge.api.aspect.IAspect> aspect,
                int amount,
                boolean simulate) {
            return transport.takeEssentia(aspect, amount, face, simulate);
        }

        @Override
        public long contentRevision() {
            // No revision to offer, and 0 is the honest answer: a caller caching on it simply refreshes.
            return 0;
        }
    }
}
