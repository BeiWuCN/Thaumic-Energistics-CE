package thaumicenergistics.blockentity;

import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.arcane.ThEArcanePattern;
import thaumicenergistics.block.ThEBaseBlockEntity;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.inventory.HandlerKnowledgeCore;
import thaumicenergistics.menu.MenuKnowledgeInscriber;

/**
 * The Knowledge Inscriber's machine half.
 *
 * <p>The player fills a 3x3 grid and the machine resolves the arcane recipe it stands for; the button writes
 * that recipe straight into the knowledge core, with no AE2 pattern in between - the core is the compressed
 * pattern, and the assembler reads it directly.
 *
 * <p>{@link #status()} is derived from the slots rather than stored, so the label stays honest without a
 * ticker, and the resolution is cached against the grid <em>and</em> the core - deleting a recipe changes the
 * core without moving the grid. The result well is drawn by {@code MenuKnowledgeInscriber.updatePreview}.
 */
public class BlockEntityKnowledgeInscriber extends ThEBaseBlockEntity {

    /** Holds the knowledge core patterns are written into. */
    public static final int CORE_SLOT = 0;
    /** Read-only mirror of the core's stored patterns, one per slot. */
    public static final int MIRROR_SLOT_START = 1;
    public static final int MIRROR_SLOT_COUNT = 21;
    /**
     * The arcane recipe's nine grid cells: the machine's real input. Written from the client through
     * {@code InscriberGridPayload}, because the grid is a ghost grid - see {@code GhostGridSlot}.
     */
    public static final int GRID_SLOT_START = MIRROR_SLOT_START + MIRROR_SLOT_COUNT;
    public static final int GRID_SLOT_COUNT = 9;
    /** Everything the block itself owns. */
    public static final int SLOT_COUNT = GRID_SLOT_START + GRID_SLOT_COUNT;

    /** Nothing to do yet: no core, or an empty grid. */
    public static final int STATUS_READY = 0;
    /**
     * Everything is in place and pressing the button will store the recipe. Distinct from {@link #STATUS_READY}
     * ("nothing to do yet"): both are benign, but the button shows "Save" for one and "No Core" for the other,
     * so they cannot share a code.
     */
    public static final int STATUS_ACTIONABLE = 7;
    /** The recipe was stored. */
    public static final int STATUS_ENCODED = 1;
    /** The grid does not correspond to any arcane recipe. */
    public static final int STATUS_NO_RECIPE = 2;
    /** The core already holds the maximum number of patterns. */
    public static final int STATUS_CORE_FULL = 3;
    /** The core already holds a pattern for that result. */
    public static final int STATUS_ALREADY_STORED = 4;
    /** The player has not unlocked the recipe's research. */
    public static final int STATUS_RESEARCH_LOCKED = 5;

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (suppressNotify) {
                return;
            }
            BlockEntityKnowledgeInscriber.this.setChanged();
            // Anything moving in the container - a grid cell, or the core being swapped - makes the cached
            // resolution stale; see resolutionDirty for why a flag rather than a signature.
            BlockEntityKnowledgeInscriber.this.resolutionDirty = true;
            // Refreshed here rather than in the menu: leaning on the menu meant leaning on a screen, and the
            // screen only exists on the client.
            BlockEntityKnowledgeInscriber.this.refreshResolution();
        }
    };

    /**
     * True while the block writes its own mirror slots. Load-bearing: {@code SimpleContainer.setItem} calls
     * {@code setChanged()} on every write, and without it the mirror refresh recurses until the stack runs out.
     */
    private boolean suppressNotify;

    /** Last action's outcome, shown by the screen. Not the button's label - see {@link #status()}. */
    private int lastResult = STATUS_READY;

    /**
     * Set when the grid or the core has moved, and cleared when the resolution is redone.
     *
     * <p>Replaced a signature string built from the slots: for a knowledge core that stringified the whole
     * stored pattern list, and the menu reads {@link #status()} once a tick, so it re-serialised the core every
     * frame. A flag suffices because this machine is the only writer, and the one path that does not announce
     * itself - a core written in place by {@code HandlerKnowledgeCore.save} - fires no {@code setChanged}.
     */
    private boolean resolutionDirty = true;

    /** The recipe the grid last resolved to, or {@code null} if it resolved to none. */
    private @Nullable ThEArcanePattern resolvedPattern;

    /** The status computed for {@link #resolvedPattern}. */
    private int resolvedStatus = STATUS_READY;

    public BlockEntityKnowledgeInscriber(BlockPos pos, BlockState state) {
        super(ModBlockEntities.KNOWLEDGE_INSCRIBER.get(), pos, state);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public boolean hasCore() {
        return inventory.getItem(CORE_SLOT).is(ModItems.KNOWLEDGE_CORE.get());
    }

    /** The nine grid cells, in reading order. */
    public List<ItemStack> gridCells() {
        List<ItemStack> cells = new ArrayList<>(GRID_SLOT_COUNT);
        for (int i = 0; i < GRID_SLOT_COUNT; i++) {
            cells.add(inventory.getItem(GRID_SLOT_START + i));
        }
        return cells;
    }

    /** Writes one grid cell, from the payload. Server side only. */
    public void setGridCell(int cell, ItemStack stack) {
        if (level == null || level.isClientSide || cell < 0 || cell >= GRID_SLOT_COUNT) {
            return;
        }
        ItemStack current = inventory.getItem(GRID_SLOT_START + cell);
        ItemStack wanted = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (ItemStack.matches(current, wanted)) {
            return;
        }
        // The write goes through the container, so setChanged fires and the grid is re-resolved there.
        inventory.setItem(GRID_SLOT_START + cell, wanted);
    }

    /**
     * Replaces the whole grid in one go, and resolves once, for loading a stored recipe: writing cell by cell
     * re-resolved a half-replaced grid on every cell, and the player watched the old recipe's items being
     * shoved out one cell at a time.
     */
    public void setGrid(List<ItemStack> cells) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasSuppressed = suppressNotify;
        suppressNotify = true;
        try {
            for (int i = 0; i < GRID_SLOT_COUNT; i++) {
                ItemStack wanted = i < cells.size() ? cells.get(i) : ItemStack.EMPTY;
                inventory.setItem(
                        GRID_SLOT_START + i, wanted.isEmpty() ? ItemStack.EMPTY : wanted.copyWithCount(1));
            }
        } finally {
            suppressNotify = wasSuppressed;
        }
        resolutionDirty = true;
        refreshResolution();
    }

    /**
     * Empties the grid, after a recipe is stored. The writes are silent because the nine of them would each
     * re-resolve a half-cleared grid; the caller marks the resolution stale once - see {@link #save}.
     */
    public void clearGrid() {
        boolean wasSuppressed = suppressNotify;
        suppressNotify = true;
        try {
            for (int i = 0; i < GRID_SLOT_COUNT; i++) {
                if (!inventory.getItem(GRID_SLOT_START + i).isEmpty()) {
                    inventory.setItem(GRID_SLOT_START + i, ItemStack.EMPTY);
                }
            }
        } finally {
            suppressNotify = wasSuppressed;
        }
    }

    // ------------------------------------------------------------------
    // Status
    // ------------------------------------------------------------------

    /**
     * What the machine would do right now, derived from the slots so that inserting a core updates the screen's
     * button immediately. Order matters: the earlier checks are the ones the player has to fix first.
     */
    public int status() {
        if (level == null) {
            return STATUS_READY;
        }
        refreshResolution();
        return resolvedStatus;
    }

    /**
     * Re-resolves the grid, if anything has moved since the last look. Called from the block's own
     * {@code setChanged}, which is what makes the machine server-driven; an earlier version leaned on the menu,
     * whose hook ran from the client's screen renderer, where no block entity is behind it.
     */
    public void refreshResolution() {
        if (level == null || level.isClientSide || !resolutionDirty) {
            return;
        }
        resolutionDirty = false;
        recompute();
    }

    /** {@code true} when pressing Save would store a recipe. */
    public boolean canStore() {
        return status() == STATUS_ACTIONABLE;
    }

    /** The arcane recipe the grid currently stands for, or {@code null}. */
    public @Nullable ThEArcanePattern currentPattern() {
        if (level == null) {
            return null;
        }
        refreshResolution();
        return resolvedPattern;
    }

    /**
     * Re-resolves the grid and caches the outcome. Server side only, by way of {@link #refreshResolution}: this
     * is where the recipe manager is scanned and the core is walked.
     */
    private void recompute() {
        resolvedPattern = null;
        resolvedStatus = STATUS_READY;
        if (level == null) {
            return;
        }
        List<ItemStack> cells = gridCells();
        if (ThEArcanePattern.isGridEmpty(cells)) {
            return;
        }
        ThEArcanePattern pattern = ThEArcanePattern.resolveGrid(level, cells);
        if (pattern == null) {
            resolvedStatus = STATUS_NO_RECIPE;
            return;
        }
        resolvedPattern = pattern;
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return;
        }
        if (core.patternFor(pattern.result()) != null) {
            resolvedStatus = STATUS_ALREADY_STORED;
            return;
        }
        if (!core.hasRoom()) {
            resolvedStatus = STATUS_CORE_FULL;
            return;
        }
        resolvedStatus = STATUS_ACTIONABLE;
    }

    private @Nullable HandlerKnowledgeCore core() {
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(inventory.getItem(CORE_SLOT), level.registryAccess());
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /**
     * Stores the resolved recipe in the core and clears the grid.
     *
     * @return the resulting status code, also available from {@link #lastResult()}
     */
    public int save(@Nullable Player player) {
        lastResult = status();
        // Re-resolve before reading, rather than trusting the cached answer: the cache is only as fresh as the
        // last change notification, and a stale one made the button do nothing at all with nothing on screen
        // to say why. Re-resolving here is one recipe lookup on a click.
        resolutionDirty = true;
        refreshResolution();
        ThEArcanePattern pattern = currentPattern();
        if (pattern == null) {
            // Say so rather than returning in silence: "Invalid" is a player's only clue that the machine did
            // not recognise the grid.
            ThaumicEnergistics.LOG.info(
                    "[inscriber] save at {} found no recipe: status={} cells={}",
                    worldPosition, resolvedStatus, gridCells().stream().filter(s -> !s.isEmpty()).count());
            return lastResult = STATUS_NO_RECIPE;
        }
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return lastResult;
        }
        if (player != null && !passesResearch(player, pattern)) {
            ThaumicEnergistics.LOG.info(
                    "[inscriber] save refused at {}: {} is gated by research this player has not unlocked",
                    worldPosition, pattern.result());
            return lastResult = STATUS_RESEARCH_LOCKED;
        }
        if (!core.store(pattern)) {
            ThaumicEnergistics.LOG.info(
                    "[inscriber] save refused at {}: the core would not take {} (room {} of {})",
                    worldPosition, pattern.result(), core.size(), HandlerKnowledgeCore.MAXIMUM_STORED_PATTERNS);
            return lastResult = STATUS_CORE_FULL;
        }
        // After both refusals, not before them: printed above them it announced a store that was about to be
        // turned down, and a log that says "storing" next to "refused" teaches the reader to distrust both.
        ThaumicEnergistics.LOG.info(
                "[inscriber] save at {} stored {} (status {})", worldPosition, pattern.result(), status());
        // Neither write announced itself - the core in place through its components, the grid with
        // notifications suppressed - so one flag covers the pair.
        resolutionDirty = true;
        clearGrid();
        return lastResult = STATUS_ENCODED;
    }

    /**
     * Removes the stored pattern for the result the grid produces. Keyed by result rather than by an index so
     * the client's selection cannot name the wrong entry: the core holds at most one pattern per result.
     *
     * @return the resulting status code
     */
    public int deleteStored(@Nullable Player player) {
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return lastResult = status();
        }
        // Only the recipe the grid resolves to, and nothing else: a fallback could remove an entry the player
        // never named, and an empty grid names nothing at all.
        ThEArcanePattern pattern = currentPattern();
        if (pattern == null) {
            return lastResult = status();
        }
        if (!core.removeByResult(pattern.result())) {
            return lastResult = status();
        }
        // The core was written in place, so nothing else reports it.
        resolutionDirty = true;
        return lastResult = status();
    }

    public int lastResult() {
        return lastResult;
    }

    /**
     * Whether this player may store the grid as it stands, for the menu's button state.
     *
     * <p>The machine's own status cannot answer this: a recipe is gated by research, and research belongs to a
     * player, not to the block. Without it the button said Save and the click did nothing.
     */
    public boolean canStore(Player player) {
        resolutionDirty = true;
        refreshResolution();
        ThEArcanePattern pattern = currentPattern();
        return pattern == null || passesResearch(player, pattern);
    }

    private boolean passesResearch(Player player, ThEArcanePattern pattern) {
        return pattern.gate().map(gate -> ResearchGate.passes(player, gate)).orElse(true);
    }

    // ------------------------------------------------------------------
    // Stored patterns
    // ------------------------------------------------------------------

    /**
     * The core's stored patterns, for anyone who needs to show them; the menu fills the 7x3 wells from this.
     *
     * <p>Not a mirror in this machine's own container: a read-only slot only shows the client what the server
     * synced into it, and this block deliberately has no update tag, so a mirror could never be kept in step.
     */
    public List<ItemStack> storedOutputs() {
        if (level == null) {
            return List.of();
        }
        HandlerKnowledgeCore core = core();
        return core == null ? List.of() : core.storedOutputs();
    }

    /** Drops the core and the pattern when the block is broken. The grid is a scratch pad, not storage. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            // The grid holds items the player still has; dropping them would duplicate whatever was dragged in
            // from JEI.
            if (i >= GRID_SLOT_START) {
                continue;
            }
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(
                        level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5,
                        worldPosition.getZ() + 0.5, stack);
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------
    // Menu, persistence, sync
    // ------------------------------------------------------------------

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MenuKnowledgeInscriber(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // ContainerHelper, not SimpleContainer.createTag: that writes only the non-empty slots and records no
        // index, while this machine's grid is mostly gaps (infusion_matrix uses four cells of nine), so a saved
        // grid came back with its gaps gone and every item shifted forwards.
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        tag.putInt("LastResult", lastResult);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // A world saved before this change kept a bare list under "Inventory", which had already lost the
            // gaps: read positionally, the most that form allows, and the next save writes the new one.
            loadLegacyInventory(tag.getList("Inventory", Tag.TAG_COMPOUND), registries);
        }
        lastResult = tag.getInt("LastResult");
    }

    /**
     * Reads the old bare-list form, by position. That form has already lost which slots its entries came from,
     * so nothing here can put the grid back together. Bounds-checked because the list may name more slots than
     * this build has.
     */
    private void loadLegacyInventory(ListTag list, HolderLookup.Provider registries) {
        int kept = Math.min(list.size(), SLOT_COUNT);
        for (int i = 0; i < kept; i++) {
            inventory.setItem(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        }
    }

    /**
     * No custom update tag: the machine's real slot - the core - is kept in step by the menu's own slot sync,
     * and pushing a container through a block update only gave the client a second, stale copy.
     */

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.thaumicenergistics.knowledge_inscriber");
    }
}
