package thaumicenergistics_ce.blockentity.vibrationchamber;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaTransport;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.util.ThETransaction;

final class ChamberEssentiaTank {

    private static final int SUCTION = 128;

    private final BlockEntityEssentiaVibrationChamber chamber;

    private final CachedEssentiaNeighbours neighbours;

    private int storedEssentia;

    private @Nullable Holder<IAspect> currentAspect;

    private long revision;

    private int tracedEssentia;

    private final SnapshotJournal<TankSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected TankSnapshot createSnapshot() {
            return new TankSnapshot(storedEssentia, tracedEssentia, currentAspect, revision);
        }

        @Override
        protected void revertToSnapshot(TankSnapshot snapshot) {
            storedEssentia = snapshot.stored();
            tracedEssentia = snapshot.traced();
            currentAspect = snapshot.aspect();
            revision = snapshot.revision();
        }
    };

    private record TankSnapshot(
            int stored,
            int traced,
            @Nullable Holder<IAspect> aspect,
            long revision) {}

    ChamberEssentiaTank(BlockEntityEssentiaVibrationChamber chamber) {
        this.chamber = chamber;
        this.neighbours = new CachedEssentiaNeighbours(chamber);
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

    int suctionAmount(boolean paused) {
        return hasRoom() && !paused ? SUCTION : 0;
    }

    AspectList contents() {
        if (storedEssentia <= 0 || currentAspect == null) {
            return AspectList.EMPTY;
        }
        return AspectList.EMPTY.add(currentAspect, storedEssentia);
    }

    @Nullable Identifier aspectId() {
        return currentAspect == null
                ? null
                : currentAspect.unwrapKey().map(ResourceKey::identifier).orElse(null);
    }

    String aspectPath() {
        return currentAspect == null
                ? ""
                : currentAspect.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
    }

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
        IEssentiaStorage storage = neighbours.storage(side);
        if (storage == null) {
            return false;
        }
        for (AspectInstance entry : storage.contents().entries()) {
            Holder<IAspect> aspect = entry.aspect();
            if (entry.amount() <= 0) {
                continue;
            }
            int taken = ThETransaction.apply(transaction -> storage.extract(aspect, 1, transaction));
            if (taken > 0) {
                accept(aspect, taken);
                return true;
            }
        }
        return false;
    }

    private boolean pullFromTube(Direction side) {
        Direction facing = side.getOpposite();
        IEssentiaTransport tube = neighbours.transport(side);
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
        chamber.markForClientUpdate();
    }

    int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction, boolean paused) {
        if (aspect == null || amount <= 0 || paused) {
            return 0;
        }
        int accepted = Math.min(amount, space());
        if (accepted <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        accept(aspect, accepted);
        return accepted;
    }

    void revertOne() {
        storedEssentia--;
        revision++;
    }

    void set(int essentia, @Nullable Holder<IAspect> aspect) {
        storedEssentia = essentia;
        currentAspect = aspect;
    }
}
