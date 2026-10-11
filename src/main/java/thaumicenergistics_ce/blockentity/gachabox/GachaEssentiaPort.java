package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAspects;
import thaumicenergistics_ce.registry.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThETransaction;

final class GachaEssentiaPort implements IEssentiaTransport {

    private static final int SUCTION = 128;

    private final BlockEntityGachaBox box;
    private final CachedEssentiaNeighbours neighbours;

    GachaEssentiaPort(BlockEntityGachaBox box) {
        this.box = box;
        this.neighbours = new CachedEssentiaNeighbours(box);
    }

    void sip(ServerLevel server) {
        int need = box.cognitio().room();
        if (need <= 0) {
            return;
        }
        Holder<IAspect> aspect = cognitio(server);
        if (aspect == null) {
            return;
        }
        Direction back = box.backFace();
        int got = 0;
        IEssentiaStorage storage = neighbours.storage(back);
        if (storage != null) {
            got += ThETransaction.apply(transaction -> storage.extract(aspect, need, transaction));
        }
        if (got < need) {
            IEssentiaTransport tube = neighbours.transport(back);
            if (tube != null) {
                got += drainTube(tube, back, aspect, need - got);
            }
        }
        box.cognitio().add(got);
    }

    private static int drainTube(IEssentiaTransport tube, Direction face, Holder<IAspect> aspect, int want) {
        Direction into = face.getOpposite();
        if (!tube.canOutputTo(into) || !aspect.equals(tube.getEssentiaType(into))) {
            return 0;
        }
        int got = 0;
        while (got < want) {
            int taken = tube.takeEssentia(aspect, want - got, into);
            if (taken <= 0) {
                break;
            }
            got += taken;
        }
        return got;
    }

    private boolean hungry() {
        return box.cognitio().wants() && box.canTurnNow();
    }

    private static @Nullable Holder<IAspect> cognitio(@Nullable Level level) {
        return level == null ? null : AEssentiaKeyType.aspectOf(level, TcAspects.COGNITIO.identifier());
    }

    @Override
    public boolean isConnectable(Direction face) {
        return face == box.backFace();
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return face == box.backFace();
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    @Override
    public void setSuction(@Nullable Holder<IAspect> aspect, int amount) {}

    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction face) {
        return face == box.backFace() && hungry() ? cognitio(box.getLevel()) : null;
    }

    @Override
    public int getSuctionAmount(Direction face) {
        return face == box.backFace() && hungry() ? SUCTION : 0;
    }

    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction face) {
        return 0;
    }

    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction face) {
        int room = spaceFor(aspect, face);
        if (amount <= 0 || room <= 0) {
            return 0;
        }
        return box.cognitio().add(Math.min(amount, room));
    }

    @Override
    public int spaceFor(Holder<IAspect> aspect, Direction face) {
        if (face != box.backFace() || !hungry() || !aspect.equals(cognitio(box.getLevel()))) {
            return 0;
        }
        return box.cognitio().room();
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        return null;
    }

    @Override
    public int getEssentiaAmount(Direction face) {
        return 0;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }
}
