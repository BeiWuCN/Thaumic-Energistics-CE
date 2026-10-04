package thaumicenergistics_ce.blockentity;

import com.leclowndu93150.thaumaturge.api.recipe.ResearchGate;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.ThaumicEnergistics;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.menu.MenuKnowledgeInscriber;

/**
 * The button writes the arcane recipe the grid resolves to straight into the knowledge core.
 * <ul>
 *   <li>{@link #status()} is derived from the slots, so the label needs no ticker.
 *   <li>The resolution is cached against the grid <em>and</em> the core: deleting a recipe moves the
 *       core, not the grid.
 * </ul>
 */
public class BlockEntityKnowledgeInscriber extends ThEBaseBlockEntity {

    public static final int CORE_SLOT = 0;
    public static final int MIRROR_SLOT_START = 1;
    public static final int MIRROR_SLOT_COUNT = 21;
    public static final int GRID_SLOT_START = MIRROR_SLOT_START + MIRROR_SLOT_COUNT;
    public static final int GRID_SLOT_COUNT = 9;
    public static final int SLOT_COUNT = GRID_SLOT_START + GRID_SLOT_COUNT;

    public static final int STATUS_READY = 0;
    public static final int STATUS_ACTIONABLE = 7;
    public static final int STATUS_ENCODED = 1;
    public static final int STATUS_NO_RECIPE = 2;
    public static final int STATUS_CORE_FULL = 3;
    public static final int STATUS_ALREADY_STORED = 4;
    public static final int STATUS_RESEARCH_LOCKED = 5;

    private final SimpleContainer inventory = new SimpleContainer(SLOT_COUNT) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (suppressNotify) {
                return;
            }
            BlockEntityKnowledgeInscriber.this.setChanged();
            BlockEntityKnowledgeInscriber.this.resolutionDirty = true;
            BlockEntityKnowledgeInscriber.this.refreshResolution();
        }
    };

    /** True while the block writes its own mirror slots: {@code setChanged()} fires on every write, so
     * without this flag the refresh recurses until the stack runs out. */
    private boolean suppressNotify;

    private int lastResult = STATUS_READY;

    private boolean resolutionDirty = true;

    private @Nullable ThEArcanePattern resolvedPattern;

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

    public List<ItemStack> gridCells() {
        List<ItemStack> cells = new ArrayList<>(GRID_SLOT_COUNT);
        for (int i = 0; i < GRID_SLOT_COUNT; i++) {
            cells.add(inventory.getItem(GRID_SLOT_START + i));
        }
        return cells;
    }

    public void setGridCell(int cell, ItemStack stack) {
        if (level == null || level.isClientSide || cell < 0 || cell >= GRID_SLOT_COUNT) {
            return;
        }
        ItemStack current = inventory.getItem(GRID_SLOT_START + cell);
        ItemStack wanted = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (ItemStack.matches(current, wanted)) {
            return;
        }
        inventory.setItem(GRID_SLOT_START + cell, wanted);
    }

    /** Cell-by-cell writes re-resolved a half-replaced grid, so the player watched the old recipe's
     * items being shoved out one cell at a time. */
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

    /** What the machine would do right now, derived from the slots so inserting a core updates the button at
     * once. Order matters: the earlier checks are the ones the player must fix first. */
    public int status() {
        if (level == null) {
            return STATUS_READY;
        }
        refreshResolution();
        return resolvedStatus;
    }

    public void refreshResolution() {
        if (level == null || level.isClientSide || !resolutionDirty) {
            return;
        }
        resolutionDirty = false;
        recompute();
    }

    public boolean canStore() {
        return status() == STATUS_ACTIONABLE;
    }

    public @Nullable ThEArcanePattern currentPattern() {
        if (level == null) {
            return null;
        }
        refreshResolution();
        return resolvedPattern;
    }

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

    /** Stores the resolved recipe in the core, clears the grid.
     * @return the resulting status code, also available from {@link #lastResult()} */
    public int save(@Nullable Player player) {
        lastResult = status();
        // The cache is only as fresh as the last change notification, and a stale one left the button
        // doing nothing with nothing on screen to say why.
        resolutionDirty = true;
        refreshResolution();
        ThEArcanePattern pattern = currentPattern();
        if (pattern == null) {
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
        // About the success path only, so it must sit after the refusals above.
        ThaumicEnergistics.LOG.info(
                "[inscriber] save at {} stored {} (status {})", worldPosition, pattern.result(), status());
        resolutionDirty = true;
        clearGrid();
        return lastResult = STATUS_ENCODED;
    }

    public int deleteStored(@Nullable Player player) {
        HandlerKnowledgeCore core = core();
        if (core == null) {
            return lastResult = status();
        }
        // Only the recipe the grid resolves to: a fallback could remove an entry the player never named.
        ThEArcanePattern pattern = currentPattern();
        if (pattern == null) {
            return lastResult = status();
        }
        if (!core.removeByResult(pattern.result())) {
            return lastResult = status();
        }
        resolutionDirty = true;
        return lastResult = status();
    }

    public int lastResult() {
        return lastResult;
    }

    /** Whether this player may store the grid as it stands, for the menu's button state. Research belongs to
     * a player, not a block, so the machine's own status cannot answer this. */
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
            // The grid holds items the player still has; dropping them would duplicate what JEI dragged in.
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
        // Not SimpleContainer.createTag: that writes only non-empty slots and records no index, so a grid
        // came back with its gaps gone and every item shifted forwards.
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
        tag.putInt("LastResult", lastResult);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // A world saved before this change kept a bare list under "Inventory", already gap-less: read it
            // positionally and the next save writes the new form.
            loadLegacyInventory(tag.getList("Inventory", Tag.TAG_COMPOUND), registries);
        }
        lastResult = tag.getInt("LastResult");
    }

    /** Reads the old bare-list form by position; that form lost which slots its entries came from, and the
     * list may name more slots than this build has. */
    private void loadLegacyInventory(ListTag list, HolderLookup.Provider registries) {
        int kept = Math.min(list.size(), SLOT_COUNT);
        for (int i = 0; i < kept; i++) {
            inventory.setItem(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        }
    }

    // No custom update tag: the core is kept in step by the menu's own slot sync, and pushing a container
    // through a block update only gave the client a second, stale copy.

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.thaumicenergistics_ce.knowledge_inscriber");
    }
}
