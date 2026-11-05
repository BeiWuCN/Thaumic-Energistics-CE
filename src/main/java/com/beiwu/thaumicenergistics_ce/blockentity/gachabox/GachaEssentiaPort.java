package com.beiwu.thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import com.beiwu.thaumicenergistics_ce.compat.thaumaturge.TcAspects;
import com.beiwu.thaumicenergistics_ce.registry.capability.CachedEssentiaNeighbours;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import com.beiwu.thaumicenergistics_ce.util.ThETransaction;

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
        if (!tube.canOutputTo(into)) {
            return 0;
        }
        int got = 0;
        while (got < want) {
            Holder<IAspect> held = tube.getEssentiaType(into);
            if (held == null || !held.equals(aspect)) {
                break;
            }
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
    public @Nullable Holder<IAspect> getSuctionType(@Nullable Direction face) {
        return hungry() ? cognitio(box.getLevel()) : null;
    }

    @Override
    public int getSuctionAmount(@Nullable Direction face) {
        return hungry() ? SUCTION : 0;
    }

    @Override
    public int getMinimumSuction() {
        return 0;
    }

    @Override
    public void setSuction(@Nullable Holder<IAspect> type, int count) {}

    @Override
    public int addEssentia(Holder<IAspect> type, int count, Direction face) {
        if (count <= 0) {
            return 0;
        }
        int accepted = Math.min(count, spaceFor(type, face));
        return accepted <= 0 ? 0 : box.cognitio().add(accepted);
    }

    @Override
    public int addEssentia(Holder<IAspect> type, int count, Direction face, boolean simulate) {
        int accepted = Math.min(Math.max(0, count), spaceFor(type, face));
        return simulate ? accepted : accepted <= 0 ? 0 : box.cognitio().add(accepted);
    }

    @Override
    public int takeEssentia(Holder<IAspect> type, int count, Direction face) {
        return 0;
    }

    @Override
    public int spaceFor(Holder<IAspect> type, Direction face) {
        return face == box.backFace() && hungry() && type.equals(cognitio(box.getLevel())) ? box.cognitio().room() : 0;
    }

    @Override
    public @Nullable Holder<IAspect> getEssentiaType(@Nullable Direction face) {
        return null;
    }

    @Override
    public int getEssentiaAmount(@Nullable Direction face) {
        return 0;
    }
}
