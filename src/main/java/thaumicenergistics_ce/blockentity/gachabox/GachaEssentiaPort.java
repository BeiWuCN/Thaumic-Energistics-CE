package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaConduit;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.compat.thaumaturge.TcAspects;
import thaumicenergistics_ce.registry.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.util.ThETransaction;

final class GachaEssentiaPort implements IEssentiaConduit {

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
            IEssentiaConduit tube = neighbours.transport(back);
            if (tube != null) {
                got += drainTube(tube, back, aspect, need - got);
            }
        }
        box.cognitio().add(got);
    }

    private static int drainTube(IEssentiaConduit tube, Direction face, Holder<IAspect> aspect, int want) {
        Direction into = face.getOpposite();
        if (!tube.releasesOn(into) || !aspect.equals(tube.heldAspect(into))) {
            return 0;
        }
        int got = 0;
        while (got < want) {
            int taken = tube.extract(aspect, want - got, into, false);
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
    public boolean connectsOn(Direction face) { return face == box.backFace(); }

    @Override
    public boolean receivesOn(Direction face) { return face == box.backFace(); }

    @Override
    public boolean releasesOn(Direction face) { return false; }

    @Override
    public com.leclowndu93150.thaumaturge.api.essentia.EssentiaPull pull(Direction face) {
        return face == box.backFace() && hungry()
                ? com.leclowndu93150.thaumaturge.api.essentia.EssentiaPull.of(cognitio(box.getLevel()), SUCTION)
                : com.leclowndu93150.thaumaturge.api.essentia.EssentiaPull.NONE;
    }

    @Override
    public int extract(Holder<IAspect> aspect, int amount, Direction face, boolean simulate) { return 0; }

    @Override
    public int insert(Holder<IAspect> aspect, int amount, Direction face, boolean simulate) {
        int accepted = Math.min(Math.max(0, amount), roomFor(aspect, face));
        return simulate ? accepted : box.cognitio().add(accepted);
    }

    @Override
    public int roomFor(Holder<IAspect> aspect, Direction face) {
        return face == box.backFace() && hungry() && aspect.equals(cognitio(box.getLevel())) ? box.cognitio().room() : 0;
    }

    @Override
    public @Nullable Holder<IAspect> heldAspect(Direction face) { return null; }

    @Override
    public int heldAmount(Direction face) { return 0; }

    @Override
    public int pullThreshold() { return 0; }
}
