package thaumicenergistics_ce.blockentity.alchemyprovider;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.registry.capability.CachedEssentiaNeighbours;
import thaumicenergistics_ce.util.ThETransaction;

final class AlchemyProviderBuffer {

    private final BlockEntityAlchemyProvider provider;

    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private final CachedEssentiaNeighbours neighbours;

    private long revision;

    private final SnapshotJournal<BufferSnapshot> journal = new SnapshotJournal<>() {
        @Override
        protected BufferSnapshot createSnapshot() {
            return new BufferSnapshot(Map.copyOf(buffer), revision);
        }

        @Override
        protected void revertToSnapshot(BufferSnapshot snapshot) {
            buffer.clear();
            buffer.putAll(snapshot.contents());
            revision = snapshot.revision();
        }

        @Override
        protected void onRootCommit(BufferSnapshot original) {
            if (revision != original.revision()) {
                provider.setChanged();
            }
        }
    };

    private record BufferSnapshot(Map<Holder<IAspect>, Integer> contents, long revision) {}

    AlchemyProviderBuffer(BlockEntityAlchemyProvider provider) {
        this.provider = provider;
        this.neighbours = new CachedEssentiaNeighbours(provider);
    }

    int buffered(Holder<IAspect> aspect) {
        return buffer.getOrDefault(aspect, 0);
    }

    long revision() {
        return revision;
    }

    boolean isEmpty() {
        return buffer.isEmpty();
    }

    void clear() {
        buffer.clear();
    }

    int insert(Holder<IAspect> aspect, int amount, TransactionContext transaction) {
        int accepted = previewInsert(aspect, amount);
        if (accepted <= 0) {
            return 0;
        }
        journal.updateSnapshots(transaction);
        buffer.put(aspect, buffer.getOrDefault(aspect, 0) + accepted);
        revision++;
        return accepted;
    }

    int previewInsert(Holder<IAspect> aspect, int amount) {
        if (aspect == null || amount <= 0 || !hasAnyTarget()) {
            return 0;
        }
        int space = BlockEntityAlchemyProvider.BUFFER_PER_ASPECT - buffer.getOrDefault(aspect, 0);
        return space <= 0 ? 0 : Math.min(amount, space);
    }

    AspectList contents() {
        if (buffer.isEmpty()) {
            return AspectList.EMPTY;
        }
        var entries = new ArrayList<AspectInstance>();
        buffer.forEach((aspect, amount) -> {
            if (amount > 0) {
                entries.add(new AspectInstance(aspect, amount));
            }
        });
        return AspectList.ofEntries(entries);
    }

    boolean hasAnyTarget() {
        for (Direction side : Direction.values()) {
            if (neighbours.storage(side) != null || SuctionTarget.on(neighbours, side) != null) {
                return true;
            }
        }
        return false;
    }

    boolean hasWork() {
        if (!buffer.isEmpty()) {
            return true;
        }
        for (Direction side : Direction.values()) {
            SuctionTarget machine = machine(side);
            if (machine != null && machine.wants() != null) {
                return true;
            }
        }
        return false;
    }

    private @Nullable SuctionTarget machine(Direction side) {
        if (neighbours.storage(side) != null) {
            return null;
        }
        return SuctionTarget.on(neighbours, side);
    }

    boolean push() {
        if (provider.getLevel() == null) {
            return false;
        }
        boolean movedAnything = topUpFromNetwork();

        var aspects = new ArrayList<>(buffer.keySet());
        for (Holder<IAspect> aspect : aspects) {
            int remaining = buffer.getOrDefault(aspect, 0);
            if (remaining <= 0) {
                buffer.remove(aspect);
                continue;
            }
            for (Direction side : Direction.values()) {
                if (remaining <= 0) {
                    break;
                }
                int accepted = hand(side, aspect, remaining);
                if (accepted > 0) {
                    remaining -= accepted;
                    movedAnything = true;
                }
            }
            if (remaining <= 0) {
                buffer.remove(aspect);
            } else {
                buffer.put(aspect, remaining);
            }
        }

        if (movedAnything) {
            revision++;
            provider.setChanged();
        }
        return movedAnything;
    }

    private int hand(Direction side, Holder<IAspect> aspect, int amount) {
        IEssentiaStorage target = neighbours.storage(side);
        if (target != null) {
            return ThETransaction.apply(transaction -> target.insert(aspect, amount, transaction));
        }
        SuctionTarget machine = SuctionTarget.on(neighbours, side);
        return machine == null ? 0 : machine.accept(aspect, amount);
    }

    private boolean topUpFromNetwork() {
        boolean fetched = false;
        for (Direction side : Direction.values()) {
            SuctionTarget machine = machine(side);
            if (machine == null) {
                continue;
            }
            Holder<IAspect> wanted = machine.wants();
            if (wanted == null || buffer.getOrDefault(wanted, 0) > 0) {
                continue;
            }
            int taken = ThETransaction.apply(transaction -> {
                int got = provider.takeForLink(wanted, BlockEntityAlchemyProvider.BUFFER_PER_ASPECT, transaction);
                if (got > 0) {
                    journal.updateSnapshots(transaction);
                    buffer.put(wanted, got);
                    revision++;
                }
                return got;
            });
            if (taken > 0) {
                fetched = true;
            }
        }
        return fetched;
    }
}
