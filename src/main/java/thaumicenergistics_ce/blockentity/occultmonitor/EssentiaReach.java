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
 * 房间能否为仪式付账：祭坛周围的容器，以及任务的每个要素各自
 * 进展如何。可用量向 [IAspectSource.containerContains] 询问，它会把我们的供应器
 * 算进去；容器列表带缓存，空结果也缓存，因为“范围内什么都没有”
 * 是常见情况。
 */
final class EssentiaReach {

    /** 祭坛周围的搜索半径：12，即 {@code EssentiaSources} 自己的容器范围。 */
    private static final int SOURCE_RANGE = 12;

    private final BlockEntityOccultMonitor monitor;

    /** 在祭坛周围找到的容器；也记录最后一次查找它们的时间。见 {@link #shortOf}。 */
    private final List<BlockPos> sourceCache = new ArrayList<>();
    private long nextSourceScan;

    private final List<EssentiaLine> essentia = new ArrayList<>();

    EssentiaReach(BlockEntityOccultMonitor monitor) {
        this.monitor = monitor;
    }

    /** 祭坛是否够不到仪式仍然需要的东西，通过
     * {@code IAspectSource.containerContains} 询问；数 {@code getAspects} 会漏掉我们的供应器。 */
    boolean shortOf(BlockPos matrixPos, @Nullable AspectList remaining) {
        Level level = monitor.getLevel();
        if (remaining == null || remaining.isEmpty() || level == null) {
            return false;
        }
        // 在这里解析一次，而不是按（要素，来源）逐个解析：[containerContains] 会遍历 ME 网络。
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
                    // 足够了：剩下的再问也没有意义。
                    break;
                }
            }
            if (reachable < entry.amount()) {
                return true;
            }
        }
        return false;
    }

    /** 祭坛自身触及范围内的容器，最多每秒重新扫描一次。 */
    private List<BlockPos> sourcesAround(BlockPos matrixPos) {
        Level level = monitor.getLevel();
        if (level == null) {
            return List.of();
        }
        long now = level.getGameTime();
        // 空结果同样缓存：“范围内没有容器”是常见情况。
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

    /** 每个仪式要素进展到哪一步：两个数字都来自任务本身，绝不来自房间扫描（扫描
     * 会随仪式进行而被抽干）。未知配方报告为 0 / n。 */
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
                // 用完整 id，不用路径：客户端解析要素时也要看命名空间。
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
