package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.crafting.IPatternDetails;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.recipe.arcane.ArcanePatternDetails;
import thaumicenergistics_ce.recipe.arcane.ThEArcanePattern;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.util.ThELog;

final class AssemblerPatternCache {

    private final BlockEntityArcaneAssembler machine;

    private boolean stale = true;
    private List<IPatternDetails> cached = List.of();

    AssemblerPatternCache(BlockEntityArcaneAssembler machine) {
        this.machine = machine;
    }

    void invalidate() {
        stale = true;
    }

    boolean isStale() {
        return stale;
    }

    List<IPatternDetails> patterns() {
        return cached;
    }

    void refresh() {
        if (stale) {
            stale = !rebuild();
        }
    }

    boolean rebuild() {
        cached = List.of();
        HandlerKnowledgeCore core = knowledgeCore();
        if (core == null) {
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
            ThELog.LOG.warn(
                    "[assembler] at {} offers {} of the {} patterns in its knowledge core",
                    machine.getBlockPos(),
                    details.size(),
                    stored.size());
        }
        if (core.unreadableCount() > 0) {
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
