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
 * The set of patterns the machine advertises to AE2, read from the knowledge core rather than from the
 * live recipe manager. A core needs registry access, so a read that could not happen yet leaves the set
 * stale and the next ask tries again.
 */
final class AssemblerPatternCache {

    private final BlockEntityArcaneAssembler machine;

    private boolean stale = true;
    private List<IPatternDetails> cached = List.of();

    AssemblerPatternCache(BlockEntityArcaneAssembler machine) {
        this.machine = machine;
    }

    /** Called when what the set is read from changed, so the set has to be read again. */
    void invalidate() {
        stale = true;
    }

    boolean isStale() {
        return stale;
    }

    /** The set as it stands; {@link #refresh()} is what brings it up to date. */
    List<IPatternDetails> patterns() {
        return cached;
    }

    /** Rebuilds the set if it is stale, and only a read that reached the core settles it. */
    void refresh() {
        if (stale) {
            stale = !rebuild();
        }
    }

    /** Rebuilds the set from the core, leaving the stale flag alone.
     * @return {@code true} when the core was readable, so the set is complete */
    boolean rebuild() {
        cached = List.of();
        HandlerKnowledgeCore core = knowledgeCore();
        if (core == null) {
            // No core, or - the case that matters - no level to read one with; report failure until it is.
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
            // Otherwise invisible: the machine just offers fewer recipes than the core holds.
            ThELog.LOG.warn(
                    "[assembler] at {} offers {} of the {} patterns in its knowledge core",
                    machine.getBlockPos(),
                    details.size(),
                    stored.size());
        }
        if (core.unreadableCount() > 0) {
            // Entries this build cannot read: kept in the item, not offered; the core would read as empty.
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
