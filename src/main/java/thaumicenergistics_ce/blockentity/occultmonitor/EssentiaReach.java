package thaumicenergistics_ce.blockentity.occultmonitor;

import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor.EssentiaLine;
import thaumicenergistics_ce.compat.thaumaturge.TcInfusion.Recipe;

/**
 * Whether the room can pay for the ritual: the containers around the altar, and how far each
 * aspect of the job has got. Availability is asked of IAspectSource.containerContains, which
 * counts our provider; and the container list is cached, empty result included, because "nothing
 * in range" is the common case.
 */
final class EssentiaReach {

    /** Search radius around an altar: twelve, {@code EssentiaSources}' own container range. */
    private static final int SOURCE_RANGE = 12;

    private final BlockEntityOccultMonitor monitor;

    /** Containers found around the altar; also when they were last looked for. See {@link #shortOf}. */
    private final List<BlockPos> sourceCache = new ArrayList<>();
    private long nextSourceScan;

    private final List<EssentiaLine> essentia = new ArrayList<>();

    EssentiaReach(BlockEntityOccultMonitor monitor) {
        this.monitor = monitor;
    }

    /** Whether the altar cannot reach what the ritual still wants, asked via
     * {@code IAspectSource.containerContains}; counting {@code getAspects} misses our provider. */
    boolean shortOf(BlockPos matrixPos, @Nullable AspectList remaining) {
        Level level = monitor.getLevel();
        if (remaining == null || remaining.isEmpty() || level == null) {
            return false;
        }
        // Resolved once here, not per (aspect, source): containerContains walks the ME network.
        List<IAspectSource> sources = new ArrayList<>(sourcesAround(matrixPos).size());
        for (BlockPos sourcePos : sourcesAround(matrixPos)) {
            if (level.getCapability(AspectCapabilities.CONTAINER, sourcePos, null)
                    instanceof IAspectSource source
                    && !source.isBlocked()) {
                sources.add(source);
            }
        }
        for (AspectInstance entry : remaining.entries()) {
            if (entry.amount() <= 0) {
                continue;
            }
            int reachable = 0;
            for (var source : sources) {
                reachable += source.containerContains(entry.aspect());
                if (reachable >= entry.amount()) {
                    // Enough: the rest would be asked for nothing.
                    break;
                }
            }
            if (reachable < entry.amount()) {
                return true;
            }
        }
        return false;
    }

    /** The containers within the altar's own reach, rescanned at most once a second. */
    private List<BlockPos> sourcesAround(BlockPos matrixPos) {
        Level level = monitor.getLevel();
        if (level == null) {
            return List.of();
        }
        long now = level.getGameTime();
        // The empty result is cached too: "no containers in range" is the common case.
        if (now < nextSourceScan) {
            return sourceCache;
        }
        nextSourceScan = now + 20;
        sourceCache.clear();
        for (BlockPos pos : BlockPos.betweenClosed(
                matrixPos.offset(-SOURCE_RANGE, -SOURCE_RANGE, -SOURCE_RANGE),
                matrixPos.offset(SOURCE_RANGE, SOURCE_RANGE, SOURCE_RANGE))) {
            if (level.getCapability(AspectCapabilities.CONTAINER, pos, null) != null) {
                sourceCache.add(pos.immutable());
            }
        }
        return sourceCache;
    }

    /** How far along each ritual aspect is: both numbers come from the job, never a room scan (which
     * drains as the ritual runs). An unknown recipe reports 0 / n. */
    void read(AspectList remaining, @Nullable Recipe recipe) {
        essentia.clear();
        AspectList total = recipe == null ? remaining : recipe.aspects();
        if (total == null || total.isEmpty()) {
            return;
        }
        for (AspectInstance entry : total.entries()) {
            if (entry.amount() <= 0) {
                continue;
            }
            int wanted = entry.amount();
            int left = remaining == null ? 0 : remaining.amountOf(entry.aspect());
            ResourceLocation id = entry.aspect().unwrapKey().map(key -> key.location()).orElse(null);
            if (id != null) {
                // Full id, not the path: the client resolves aspects by namespace too.
                essentia.add(new EssentiaLine(id.toString(), Math.max(0, wanted - left), wanted));
            }
        }
    }

    List<EssentiaLine> lines() {
        return essentia;
    }

    void clear() {
        essentia.clear();
    }
}
