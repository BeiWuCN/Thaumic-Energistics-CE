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
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.init.capability.CachedEssentiaNeighbours;

/**
 * The provider's buffer: essentia held on its way out to the world, one tick at a time.
 * <ul>
 *   <li>A waypoint, not storage: nothing here is saved, and a reload starts empty.
 *   <li>A provider with nothing attached refuses everything; a machine's suction is fetched from the grid.
 *   <li>Every change bumps the revision, the one answer a cache of this container needs.
 * </ul>
 */
final class AlchemyProviderBuffer {

    private final BlockEntityAlchemyProvider provider;

    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private final CachedEssentiaNeighbours neighbours;

    private long revision;

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

    int insert(Holder<IAspect> aspect, int amount, boolean simulate) {
        if (aspect == null || amount <= 0 || !hasAnyTarget()) {
            return 0;
        }
        int held = buffer.getOrDefault(aspect, 0);
        int space = BlockEntityAlchemyProvider.BUFFER_PER_ASPECT - held;
        if (space <= 0) {
            return 0;
        }
        int accepted = Math.min(amount, space);
        if (!simulate) {
            buffer.put(aspect, held + accepted);
            revision++;
            provider.setChanged();
        }
        return accepted;
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

    /** True when any neighbour takes essentia, so an insert has somewhere to go. */
    boolean hasAnyTarget() {
        for (Direction side : Direction.values()) {
            if (neighbours.storage(side) != null || SuctionTarget.on(neighbours, side) != null) {
                return true;
            }
        }
        return false;
    }

    /** True when there is work: essentia waiting to leave, or a machine asking for some. */
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

    /** The machine worth feeding on that side: only where no container sits to be inserted into. */
    private @Nullable SuctionTarget machine(Direction side) {
        if (neighbours.storage(side) != null) {
            return null;
        }
        return SuctionTarget.on(neighbours, side);
    }

    /** Hands the buffer to the neighbours, sides in turn, and says whether anything moved. */
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

    /** A container takes an insert; a machine that wants essentia is handed the same amount instead. */
    private int hand(Direction side, Holder<IAspect> aspect, int amount) {
        IEssentiaStorage target = neighbours.storage(side);
        if (target != null) {
            return target.insert(aspect, amount, false);
        }
        SuctionTarget machine = SuctionTarget.on(neighbours, side);
        return machine == null ? 0 : machine.accept(aspect, amount);
    }

    /**
     * Fetches what a suction machine asks for: the buffer is a waypoint and the grid is the source, so
     * a machine with no container beside it would otherwise wait for an insert that never comes.
     */
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
            int taken = provider.takeForLink(wanted, BlockEntityAlchemyProvider.BUFFER_PER_ASPECT, false);
            if (taken > 0) {
                buffer.put(wanted, taken);
                fetched = true;
            }
        }
        return fetched;
    }
}
