package thaumicenergistics_ce.blockentity.vibrationchamber;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

/**
 * The chamber's fuel slot: a count of essentia under the aspect put in last, and the pulling that fills it.
 * <ul>
 *   <li>The buffer is a count, not an aspect list, so the aspect kept is for display only.
 *   <li>Every change lands here, so the revision bumped with it is the one answer a cache needs.
 *   <li>What a pipe sees - room, suction, contents - is read off this slot, never off the burn.
 * </ul>
 */
final class ChamberEssentiaTank {

    private static final int SUCTION = 128;

    private final BlockEntityEssentiaVibrationChamber chamber;

    private int storedEssentia;

    private @Nullable Holder<IAspect> currentAspect;

    /** Bumped whenever the buffer changes, so a cache of this container's contents notices. */
    private long revision;

    /** What the trace has seen arrive since its last line, so a pipe that never reaches can be told apart. */
    private int tracedEssentia;

    ChamberEssentiaTank(BlockEntityEssentiaVibrationChamber chamber) {
        this.chamber = chamber;
    }

    int amount() {
        return storedEssentia;
    }

    @Nullable Holder<IAspect> aspect() {
        return currentAspect;
    }

    long revision() {
        return revision;
    }

    int traced() {
        return tracedEssentia;
    }

    void resetTraced() {
        tracedEssentia = 0;
    }

    boolean hasRoom() {
        return storedEssentia < BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA;
    }

    int space() {
        return Math.max(0, BlockEntityEssentiaVibrationChamber.MAX_ESSENTIA - storedEssentia);
    }

    /** Zero when the buffer or the energy slot is full: pipes steer by this number, and a full machine
     * advertising suction would draw essentia it cannot burn. */
    int suctionAmount(boolean paused) {
        return hasRoom() && !paused ? SUCTION : 0;
    }

    /** The whole buffer under the aspect put in last: the buffer is a count, so this answer is lossy. */
    AspectList contents() {
        if (storedEssentia <= 0 || currentAspect == null) {
            return AspectList.EMPTY;
        }
        return AspectList.EMPTY.add(currentAspect, storedEssentia);
    }

    /** The held aspect's id, or null when nothing is held. */
    @Nullable ResourceLocation aspectId() {
        return currentAspect == null
                ? null
                : currentAspect.unwrapKey().map(ResourceKey::location).orElse(null);
    }

    /** The held aspect's path, or empty when nothing is held; the burn is priced off this. */
    String aspectPath() {
        return currentAspect == null
                ? ""
                : currentAspect.unwrapKey().map(key -> key.location().getPath()).orElse("");
    }

    /**
     * Draws one unit from a neighbouring container: there is no "as much as fits" call, and returning an
     * excess is where essentia gets lost.
     */
    void pull() {
        if (chamber.getLevel() == null || !hasRoom()) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (pullFromContainer(side) || pullFromTube(side)) {
                return;
            }
        }
    }

    private boolean pullFromContainer(Direction side) {
        IEssentiaStorage storage = chamber.getLevel().getCapability(
                EssentiaCapabilities.STORAGE, chamber.getBlockPos().relative(side), side.getOpposite());
        if (storage == null) {
            return false;
        }
        for (AspectInstance entry : storage.contents().entries()) {
            Holder<IAspect> aspect = entry.aspect();
            if (entry.amount() <= 0) {
                continue;
            }
            int taken = storage.extract(aspect, 1, false);
            if (taken > 0) {
                accept(aspect, taken);
                return true;
            }
        }
        return false;
    }

    /**
     * Pulls from a Thaumaturge essentia tube, which does not push into the machines it passes: the
     * destination is the side that asks, as in Thaumaturge's port. The tests below are that port's.
     */
    private boolean pullFromTube(Direction side) {
        Direction facing = side.getOpposite();
        IEssentiaTransport tube = chamber.getLevel().getCapability(
                EssentiaCapabilities.TRANSPORT, chamber.getBlockPos().relative(side), facing);
        if (tube == null || !tube.canOutputTo(facing)) {
            return false;
        }
        if (tube.getEssentiaAmount(facing) <= 0
                || tube.getSuctionAmount(facing) >= chamber.getSuctionAmount(side)
                || chamber.getSuctionAmount(side) < tube.getMinimumSuction()) {
            return false;
        }
        Holder<IAspect> aspect = tube.getEssentiaType(facing);
        if (aspect == null) {
            return false;
        }
        int taken = tube.takeEssentia(aspect, 1, facing);
        if (taken > 0) {
            accept(aspect, taken);
            return true;
        }
        return false;
    }

    /** Takes what fits under the aspect offered; the aspect put in last is the one the buffer is read as. */
    void accept(Holder<IAspect> aspect, int amount) {
        int taken = Math.min(amount, space());
        if (taken <= 0) {
            return;
        }
        storedEssentia += taken;
        tracedEssentia += taken;
        currentAspect = aspect;
        revision++;
        chamber.setChanged();
        // The tooltip reads the buffer client-side; fuel arrives a unit at a time, not per tick.
        chamber.markForClientUpdate();
    }

    /** What a fill takes, capped, and applied only when it is not a simulation. */
    int insert(Holder<IAspect> aspect, int amount, boolean simulate, boolean paused) {
        // Same rule as the pull and the suction: a full slot takes nothing, however it is offered.
        if (aspect == null || amount <= 0 || paused) {
            return 0;
        }
        // Floored at 0: a saved count above the cap would go negative, read as "nothing taken".
        int accepted = Math.min(amount, space());
        if (accepted > 0 && !simulate) {
            accept(aspect, accepted);
        }
        return accepted;
    }

    /** Gives up one unit of fuel, counted as a change of contents like any other. */
    void revertOne() {
        storedEssentia--;
        revision++;
    }

    /** Puts the count and the aspect in as given, whether they came off the wire or out of a saved tag. */
    void set(int essentia, @Nullable Holder<IAspect> aspect) {
        storedEssentia = essentia;
        currentAspect = aspect;
    }
}
