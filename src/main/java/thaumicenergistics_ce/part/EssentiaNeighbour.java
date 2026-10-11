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
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.essentia.ThEImmediateStorage;

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

    private record Adapter(IEssentiaTransport transport, Direction face)
            implements IEssentiaStorage, ThEImmediateStorage {

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
        public int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
            return transport.addEssentia(aspect, amount, face);
        }

        @Override
        public int extract(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
            return transport.takeEssentia(aspect, amount, face);
        }

        @Override
        public int previewInsert(Holder<IAspect> aspect, int amount) {
            return transport.addEssentia(aspect, amount, face, true);
        }

        @Override
        public int previewExtract(Holder<IAspect> aspect, int amount) {
            return transport.takeEssentia(aspect, amount, face, true);
        }

        @Override
        public long contentRevision() {
            return 0;
        }
    }
}
