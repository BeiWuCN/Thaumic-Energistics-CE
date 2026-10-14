package thaumicenergistics_ce.part;

import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaConduit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.integration.ae2.essentia.ThEImmediateStorage;

public final class EssentiaNeighbour {

    private static final Direction[] FACES = Direction.values();

    private EssentiaNeighbour() {}

    public static @Nullable IEssentiaStorage find(Level level, BlockPos target, Direction from) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        if (!server.isLoaded(target)) {
            return null;
        }

        IEssentiaConduit transport = com.leclowndu93150.thaumaturge.api.essentia.EssentiaAccess.conduit(server, target, from);
        if (transport == null) {
            return server.getCapability(EssentiaCapabilities.STORAGE, target, from);
        }
        if (!transport.connectsOn(from)) {
            return null;
        }

        for (Direction accepted : FACES) {
            if (!transport.connectsOn(accepted)) {
                continue;
            }
            IEssentiaStorage storage = server.getCapability(EssentiaCapabilities.STORAGE, target, accepted);
            if (storage != null) {
                return storage;
            }
        }

        return new Adapter(transport, from);
    }

    private record Adapter(IEssentiaConduit transport, Direction face)
            implements IEssentiaStorage, ThEImmediateStorage {

        @Override
        public AspectList contents() {
            var aspect = transport.heldAspect(face);
            int amount = transport.heldAmount(face);
            return aspect == null || amount <= 0
                    ? AspectList.EMPTY
                    : AspectList.EMPTY.add(aspect, amount);
        }

        @Override
        public int amount(Holder<IAspect> aspect) {
            var held = transport.heldAspect(face);
            return held != null && held.equals(aspect) ? transport.heldAmount(face) : 0;
        }

        @Override
        public int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
            return transport.insert(aspect, amount, face, false);
        }

        @Override
        public int extract(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
            return transport.extract(aspect, amount, face, false);
        }

        @Override
        public int previewInsert(Holder<IAspect> aspect, int amount) {
            return transport.insert(aspect, amount, face, true);
        }

        @Override
        public int previewExtract(Holder<IAspect> aspect, int amount) {
            return transport.extract(aspect, amount, face, true);
        }

        @Override
        public long contentRevision() {
            return 0;
        }
    }
}
