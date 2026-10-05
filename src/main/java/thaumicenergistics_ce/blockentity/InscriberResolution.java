package thaumicenergistics_ce.blockentity;

import static thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE;
import static thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber.STATUS_ALREADY_STORED;
import static thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber.STATUS_CORE_FULL;
import static thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber.STATUS_ENCODED;
import static thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber.STATUS_NO_RECIPE;
import static thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber.STATUS_READY;
import static thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED;

import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.util.ThELog;

/**
 * What the inscriber's grid resolves to, and what its button would do with that recipe right now.
 * <ul>
 *   <li>The cache is keyed on the grid <em>and</em> the core: deleting a recipe moves the core, not the grid.
 *   <li>Every code comes from the slots, so a label or a button needs no ticker.
 * </ul>
 */
final class InscriberResolution {

    private final BlockEntityKnowledgeInscriber inscriber;
    private final InscriberInventory inventory;

    private boolean dirty = true;
    private @Nullable ThEArcanePattern pattern;
    private int status = STATUS_READY;
    private int lastResult = STATUS_READY;

    InscriberResolution(BlockEntityKnowledgeInscriber inscriber, InscriberInventory inventory) {
        this.inscriber = inscriber;
        this.inventory = inventory;
    }

    void markDirty() {
        dirty = true;
    }

    /** What the machine would do right now, derived from the slots so inserting a core updates the button
     * at once. Order matters: the earlier checks are the ones the player must fix first. */
    int status() {
        if (inscriber.getLevel() == null) {
            return STATUS_READY;
        }
        refresh();
        return status;
    }

    void refresh() {
        Level level = inscriber.getLevel();
        if (level == null || level.isClientSide || !dirty) {
            return;
        }
        dirty = false;
        recompute();
    }

    boolean canStore() {
        return status() == STATUS_ACTIONABLE;
    }

    @Nullable ThEArcanePattern pattern() {
        if (inscriber.getLevel() == null) {
            return null;
        }
        refresh();
        return pattern;
    }

    /** Stores the resolved recipe in the core, clears the grid.
     * @return the resulting status code, also available from {@link #lastResult()} */
    int save(@Nullable Player player) {
        lastResult = status();
        // The cache is only as fresh as the last change notification, and a stale one left the button
        // doing nothing with nothing on screen to say why.
        dirty = true;
        refresh();
        ThEArcanePattern resolved = pattern();
        if (resolved == null) {
            ThELog.LOG.info("[inscriber] save at {} found no recipe: status={} cells={}",
                    inscriber.getBlockPos(), status,
                    inventory.cells().stream().filter(s -> !s.isEmpty()).count());
            return lastResult = STATUS_NO_RECIPE;
        }
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return lastResult;
        }
        if (player != null && !passesResearch(player, resolved)) {
            ThELog.LOG.info(
                    "[inscriber] save refused at {}: {} is gated by research this player has not unlocked",
                    inscriber.getBlockPos(), resolved.result());
            return lastResult = STATUS_RESEARCH_LOCKED;
        }
        if (!core.store(resolved)) {
            ThELog.LOG.info("[inscriber] save refused at {}: the core would not take {} (room {} of {})",
                    inscriber.getBlockPos(), resolved.result(), core.size(),
                    HandlerKnowledgeCore.MAXIMUM_STORED_PATTERNS);
            return lastResult = STATUS_CORE_FULL;
        }
        // About the success path only, so it must sit after the refusals above.
        ThELog.LOG.info("[inscriber] save at {} stored {} (status {})",
                inscriber.getBlockPos(), resolved.result(), status());
        dirty = true;
        inventory.clear();
        return lastResult = STATUS_ENCODED;
    }

    int deleteStored(@Nullable Player player) {
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return lastResult = status();
        }
        // Only the recipe the grid resolves to: a fallback could remove an entry the player never named.
        ThEArcanePattern resolved = pattern();
        if (resolved == null) {
            return lastResult = status();
        }
        if (!core.removeByResult(resolved.result())) {
            return lastResult = status();
        }
        dirty = true;
        return lastResult = status();
    }

    int lastResult() {
        return lastResult;
    }

    /** Whether this player may store the grid as it stands, for the menu's button state. Research belongs to
     * a player, not a block, so the machine's own status cannot answer this. */
    boolean canStore(Player player) {
        dirty = true;
        refresh();
        ThEArcanePattern resolved = pattern();
        return resolved == null || passesResearch(player, resolved);
    }

    List<ItemStack> storedOutputs() {
        if (inscriber.getLevel() == null) {
            return List.of();
        }
        HandlerKnowledgeCore core = core();
        return core == null ? List.of() : core.storedOutputs();
    }

    void writeTo(CompoundTag tag) {
        tag.putInt("LastResult", lastResult);
    }

    void readFrom(CompoundTag tag) {
        lastResult = tag.getInt("LastResult");
    }

    private void recompute() {
        pattern = null;
        status = STATUS_READY;
        Level level = inscriber.getLevel();
        if (level == null) {
            return;
        }
        List<ItemStack> cells = inventory.cells();
        if (ThEArcanePattern.isGridEmpty(cells)) {
            return;
        }
        ThEArcanePattern resolved = ThEArcanePattern.resolveGrid(level, cells);
        if (resolved == null) {
            status = STATUS_NO_RECIPE;
            return;
        }
        pattern = resolved;
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return;
        }
        if (core.patternFor(resolved.result()) != null) {
            status = STATUS_ALREADY_STORED;
            return;
        }
        if (!core.hasRoom()) {
            status = STATUS_CORE_FULL;
            return;
        }
        status = STATUS_ACTIONABLE;
    }

    private @Nullable HandlerKnowledgeCore core() {
        Level level = inscriber.getLevel();
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(inventory.coreStack(), level.registryAccess());
    }

    private boolean passesResearch(Player player, ThEArcanePattern resolved) {
        return resolved.gate().map(gate -> ResearchGate.passes(player, gate)).orElse(true);
    }
}
