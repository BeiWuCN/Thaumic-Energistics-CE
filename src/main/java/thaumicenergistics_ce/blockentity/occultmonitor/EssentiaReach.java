package thaumicenergistics_ce.blockentity.occultmonitor;

import com.leclowndu93150.thaumaturge.api.aspect.AspectCapabilities;
import com.leclowndu93150.thaumaturge.api.aspect.AspectInstance;
import com.leclowndu93150.thaumaturge.api.aspect.AspectList;
import com.leclowndu93150.thaumaturge.api.aspect.IAspectSource;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.occultmonitor.BlockEntityOccultMonitor.EssentiaLine;
import thaumicenergistics_ce.compat.thaumaturge.TcInfusion.Recipe;

final class EssentiaReach {

    private static final int SOURCE_RANGE = 12;

    private final BlockEntityOccultMonitor monitor;

    private final List<BlockPos> sourceCache = new ArrayList<>();
    private long nextSourceScan;

    private final List<EssentiaLine> essentia = new ArrayList<>();

    EssentiaReach(BlockEntityOccultMonitor monitor) {
        this.monitor = monitor;
    }

    boolean shortOf(BlockPos matrixPos, @Nullable AspectList remaining) {
        Level level = monitor.getLevel();
        if (remaining == null || remaining.isEmpty() || level == null) {
            return false;
        }
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
                reachable += source.amountOf(entry.aspect());
                if (reachable >= entry.amount()) {
                    break;
                }
            }
            if (reachable < entry.amount()) {
                return true;
            }
        }
        return false;
    }

    private List<BlockPos> sourcesAround(BlockPos matrixPos) {
        Level level = monitor.getLevel();
        if (level == null) {
            return List.of();
        }
        long now = level.getGameTime();
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
            Identifier id = entry.aspect().unwrapKey().map(key -> key.identifier()).orElse(null);
            if (id != null) {
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
