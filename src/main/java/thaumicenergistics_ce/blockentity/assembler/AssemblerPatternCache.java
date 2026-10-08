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
 * 机器向 AE2 公布的样板集，从知识核心读取，不读运行中的配方管理器。
 * 核心要注册表访问，一次还读不了的读取会让集合保持过期，下次请求重试。
 */
final class AssemblerPatternCache {

    private final BlockEntityArcaneAssembler machine;

    private boolean stale = true;
    private List<IPatternDetails> cached = List.of();

    AssemblerPatternCache(BlockEntityArcaneAssembler machine) {
        this.machine = machine;
    }

    /** 读取来源变了、集合要重读时调用。 */
    void invalidate() {
        stale = true;
    }

    boolean isStale() {
        return stale;
    }

    /** 集合当前的样子；{@link #refresh()} 负责更新到最新。 */
    List<IPatternDetails> patterns() {
        return cached;
    }

    /** 集合过期时重建它，只有真正读到核心的那次读取才清掉过期标志。 */
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
            // 没有核心，或者没有能读核心的 level；在可以之前报告失败。
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
            // 否则不可见：机器提供的配方比核心持有的少。
            ThELog.LOG.warn(
                    "[assembler] at {} offers {} of the {} patterns in its knowledge core",
                    machine.getBlockPos(),
                    details.size(),
                    stored.size());
        }
        if (core.unreadableCount() > 0) {
            // 否则不可见：机器提供的配方比核心持有的少。
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
