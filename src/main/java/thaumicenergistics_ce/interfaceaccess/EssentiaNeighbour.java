package thaumicenergistics_ce.interfaceaccess;

import appeng.helpers.InterfaceLogicHost;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * What one face of an interface gives back as essentia storage: a container as it is, or a pipe wrapped
 * to fit. Items are never asked for, so this is the whole of the neighbour side of a round.
 */
final class EssentiaNeighbour {

    private EssentiaNeighbour() {}

    /**
     * The essentia storage a face gives back, or {@code null} for a face that has none. Items are never
     * asked for: the player's rule is that this card moves essentia, not what a chest would take.
     */
    static @Nullable IEssentiaStorage at(InterfaceLogicHost host, Direction face) {
        BlockEntity be = host.getBlockEntity();
        if (be == null || !(be.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        BlockPos neighbour = be.getBlockPos().relative(face);
        Direction from = face.getOpposite();
        if (!level.isLoaded(neighbour)) {
            return null;
        }
        // A pipe answers TRANSPORT rather than STORAGE, so it is asked first and wrapped to fit.
        IEssentiaTransport tube = level.getCapability(EssentiaCapabilities.TRANSPORT, neighbour, from);
        if (tube != null && tube.isConnectable(from)) {
            return new TubeStorage(tube, from);
        }
        return level.getCapability(EssentiaCapabilities.STORAGE, neighbour, from);
    }

    /**
     * A pipe read as a container. Essentia on a pipe lives per face rather than as one store, so one
     * adapter serves one face and its revision is of no use to a caller that reads every round.
     */
    private record TubeStorage(IEssentiaTransport transport, Direction face) implements IEssentiaStorage {

        @Override
        public AspectList contents() {
            Holder<IAspect> held = transport.getEssentiaType(face);
            int amount = transport.getEssentiaAmount(face);
            return held == null || amount <= 0 ? AspectList.EMPTY : AspectList.EMPTY.add(held, amount);
        }

        @Override
        public int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
            return transport.addEssentia(aspect, amount, face, simulate);
        }

        @Override
        public int extract(Holder<IAspect> aspect, int amount, boolean simulate) {
            return transport.takeEssentia(aspect, amount, face, simulate);
        }

        @Override
        public long contentRevision() {
            return 0;
        }
    }
}
