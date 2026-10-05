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

    private static final Direction[] FACES = Direction.values();

    private EssentiaNeighbour() {}

    /**
     * Deliberately uncached, alone among this mod's neighbour lookups: the target is not a block the
     * caller owns, and the part's host never says the neighbour was re-packed. Asking is cheaper.
     */
    static @Nullable IEssentiaStorage find(Level level, BlockPos target, Direction from) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }

        IEssentiaTransport transport = server.getCapability(EssentiaCapabilities.TRANSPORT, target, from);
        if (transport == null) {
            return server.getCapability(EssentiaCapabilities.STORAGE, target, from);
        }
        if (!transport.isConnectable(from)) {
            return null;
        }

        for (Direction accepted : FACES) {
            if (!transport.isConnectable(accepted)) {
                continue;
            }
            IEssentiaStorage storage = server.getCapability(EssentiaCapabilities.STORAGE, target, accepted);
            if (storage != null) {
                return storage;
            }
        }

        return new Adapter(transport, from);
    }

    /** Directional by necessity: a pipe's contents differ per face, so one adapter serves one face. */
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
            return 0;
        }
    }
}
