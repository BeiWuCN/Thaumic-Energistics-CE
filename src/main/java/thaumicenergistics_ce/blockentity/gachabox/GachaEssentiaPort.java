package thaumicenergistics_ce.blockentity.gachabox;

import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.aspect.TCAspects;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * The box's essentia port, on the face behind the screen. It both asks and is asked: the box pulls
 * from whatever sits against that face, and the suction it reports while a turn is short of
 * cognitio is what sets a tube line moving. A tube only grows an arm toward a neighbour that
 * answers this capability, so the port is also what makes the box visible to a line at all.
 */
final class GachaEssentiaPort implements IEssentiaTransport {

    /** The suction the box reports while a turn is short of cognitio: the strength Thaumaturge's own
     * essentia port asks with, which is enough to out-pull a jar and set a whole line moving. */
    private static final int SUCTION = 128;

    private final BlockEntityGachaBox box;
    private final CachedEssentiaNeighbours neighbours;

    GachaEssentiaPort(BlockEntityGachaBox box) {
        this.box = box;
        this.neighbours = new CachedEssentiaNeighbours(box);
    }

    /** Banks what the face behind the box is holding out, up to what the next turn still needs. */
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
            got += storage.extract(aspect, need, false);
        }
        if (got < need) {
            IEssentiaTransport tube = neighbours.transport(back);
            if (tube != null) {
                got += drainTube(tube, back, aspect, need - got);
            }
        }
        box.cognitio().add(got);
    }

    /** Takes from a tube a call at a time: a tube carries one point and hands over one per call, so
     * a tube line fills the reserve over as many seconds as it has points to give. */
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

    /** Whether a point would be welcome right now: the box can turn and is short of a turn's fuel. */
    private boolean hungry() {
        return box.cognitio().wants() && box.canTurnNow();
    }

    /** Cognitio as the level hands it out, for the capability answers that arrive without a server. */
    private static @Nullable Holder<IAspect> cognitio(@Nullable Level level) {
        return level == null ? null : AEssentiaKeyType.aspectOf(level, TCAspects.COGNITIO.location());
    }

    /** Essentia arrives through the face behind the screen and through no other, which is also where
     * a tube line has to end for its arm to form at all. */
    @Override
    public boolean isConnectable(Direction face) {
        return face == box.backFace();
    }

    /** The back face is an inlet; a tube arm anywhere else is not the box's to take. */
    @Override
    public boolean canInputFrom(Direction face) {
        return face == box.backFace();
    }

    /** Nothing is ever drawn back out: the reserve is fuel for a turn, not stock to be shared. */
    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    /** The box makes its own suction out of what the turn still needs, so a tube cannot set it. */
    @Override
    public void setSuction(@Nullable Holder<IAspect> aspect, int amount) {}

    /** What the box is asking for: cognitio, and only while it is short of a turn's worth. */
    @Override
    public @Nullable Holder<IAspect> getSuctionType(Direction face) {
        return face == box.backFace() && hungry() ? cognitio(box.getLevel()) : null;
    }

    /** The suction that sets a line moving: a tube follows its hungriest neighbour, so a box that
     * cannot turn asks for nothing rather than emptying a jar it has no use for yet. */
    @Override
    public int getSuctionAmount(Direction face) {
        return face == box.backFace() && hungry() ? SUCTION : 0;
    }

    /** Zero: every point the box holds is already spoken for by the next turn it pays for. */
    @Override
    public int takeEssentia(Holder<IAspect> aspect, int amount, Direction face) {
        return 0;
    }

    /** Banks a point pushed in through the back face, for a line that hands over what it carries. */
    @Override
    public int addEssentia(Holder<IAspect> aspect, int amount, Direction face) {
        int room = spaceFor(aspect, face);
        if (amount <= 0 || room <= 0) {
            return 0;
        }
        return box.cognitio().add(Math.min(amount, room));
    }

    /** The reserve never holds more than one turn costs, so the room is what is still missing. */
    @Override
    public int spaceFor(Holder<IAspect> aspect, Direction face) {
        if (face != box.backFace() || !hungry() || !aspect.equals(cognitio(box.getLevel()))) {
            return 0;
        }
        return box.cognitio().room();
    }

    /** Nothing sits in the box to route: what has been banked is not a container a tube can read. */
    @Override
    public @Nullable Holder<IAspect> getEssentiaType(Direction face) {
        return null;
    }

    /** Zero for the same reason: a tube reading the box finds nothing it may take away. */
    @Override
    public int getEssentiaAmount(Direction face) {
        return 0;
    }

    /** No threshold: a line may hand a point over on its own terms rather than match a strength. */
    @Override
    public int getMinimumSuction() {
        return 0;
    }
}
