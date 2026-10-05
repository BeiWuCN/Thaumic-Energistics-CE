package thaumicenergistics_ce.blockentity.essentiaprovider;

import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.api.essentia.IEssentiaStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;

/**
 * The provider's buffer: essentia held on its way out to the world, one tick at a time.
 * <ul>
 *   <li>A waypoint, not storage: nothing here is saved, and a reload starts empty.
 *   <li>A provider with nothing attached refuses everything, so the grid is never told of room it has.
 *   <li>Every change bumps the revision, the one answer a cache of this container needs.
 * </ul>
 */
final class EssentiaProviderBuffer {

    private final BlockEntityEssentiaProvider provider;

    private final Map<Holder<IAspect>, Integer> buffer = new HashMap<>();

    private long revision;

    EssentiaProviderBuffer(BlockEntityEssentiaProvider provider) {
        this.provider = provider;
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
        int space = BlockEntityEssentiaProvider.BUFFER_PER_ASPECT - held;
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
        Level level = provider.getLevel();
        if (level == null) {
            return false;
        }
        for (Direction side : Direction.values()) {
            if (level.getCapability(EssentiaCapabilities.STORAGE,
                            provider.getBlockPos().relative(side), side.getOpposite())
                    != null) {
                return true;
            }
        }
        return false;
    }

    /** Hands the buffer to the neighbours, sides in turn, and says whether anything moved. */
    boolean push() {
        Level level = provider.getLevel();
        if (level == null) {
            return false;
        }
        boolean movedAnything = false;

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
                IEssentiaStorage target = level.getCapability(
                        EssentiaCapabilities.STORAGE,
                        provider.getBlockPos().relative(side),
                        side.getOpposite());
                if (target == null) {
                    continue;
                }
                int accepted = target.insert(aspect, remaining, false);
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
}
