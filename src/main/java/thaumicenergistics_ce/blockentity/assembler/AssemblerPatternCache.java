package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.crafting.IPatternDetails;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.util.ThELog;

/**
 * 机器向 AE2 公布的样板集，从知识核心而不是运行中的配方管理器读取。
 * 核心需要注册表访问，所以一次还无法进行的读取会让集合保持过期，
 * 下一次请求会重试。
 */
final class AssemblerPatternCache {

    private final BlockEntityArcaneAssembler machine;

    private boolean stale = true;
    private List<IPatternDetails> cached = List.of();

    AssemblerPatternCache(BlockEntityArcaneAssembler machine) {
        this.machine = machine;
    }

    /** 在读取来源发生变化、必须重新读取集合时调用。 */
    void invalidate() {
        stale = true;
    }

    boolean isStale() {
        return stale;
    }

    /** 集合当前的样子；{@link #refresh()} 负责把它更新到最新。 */
    List<IPatternDetails> patterns() {
        return cached;
    }

    /** 集合过期时重建它，只有真正读到核心的那次读取才结算过期标志。 */
    void refresh() {
        if (stale) {
            stale = !rebuild();
        }
    }

    /** 从核心重建集合，不动过期标志。
     * @return 核心可读、集合因此完整时为 {@code true} */
    boolean rebuild() {
        cached = List.of();
        HandlerKnowledgeCore core = knowledgeCore();
        if (core == null) {
            // 没有核心，或者——更关键的情况——没有可用来读取核心的 level；在可以之前报告失败。
            return machine.getLevel() != null;
        }
        List<IPatternDetails> details = new ArrayList<>();
        List<ThEArcanePattern> stored = core.patterns();
        for (ThEArcanePattern pattern : stored) {
            ArcanePatternDetails detail = ArcanePatternDetails.of(
                    pattern,
                    machine.getLevel().registryAccess(),
                    why -> ThELog.LOG.warn(
                            "[assembler] at {} is not offering the stored pattern for {}: {}",
                            machine.getBlockPos(),
                            pattern.result(),
                            why));
            if (detail != null) {
                details.add(detail);
            }
        }
        if (details.size() < stored.size()) {
            // 否则不可见：机器只是提供的配方比核心持有的少。
            ThELog.LOG.warn(
                    "[assembler] at {} offers {} of the {} patterns in its knowledge core",
                    machine.getBlockPos(),
                    details.size(),
                    stored.size());
        }
        if (core.unreadableCount() > 0) {
            // 本次构建无法读取的条目：留在物品里但不公布；否则核心会被读成空的。
            ThELog.LOG.warn(
                    "[assembler] at {} cannot read {} entr(ies) in its knowledge core; they are kept in the"
                            + " item and {} pattern(s) are offered",
                    machine.getBlockPos(),
                    core.unreadableCount(),
                    details.size());
        }
        cached = List.copyOf(details);
        return true;
    }

    private @Nullable HandlerKnowledgeCore knowledgeCore() {
        if (machine.getLevel() == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(
                machine.inventory.getItem(BlockEntityArcaneAssembler.CORE_SLOT),
                machine.getLevel().registryAccess());
    }
}
