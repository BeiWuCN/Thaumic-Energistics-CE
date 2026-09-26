package thaumicenergistics.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.arcane.ThEArcanePattern;
import thaumicenergistics.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics.init.ModItems;
import thaumicenergistics.init.ModMenuTypes;
import thaumicenergistics.inventory.HandlerKnowledgeCore;
import thaumicenergistics.menu.slot.GhostGridSlot;
import thaumicenergistics.menu.slot.MachineGridSlot;
import thaumicenergistics.menu.slot.ReadOnlySlot;

/**
 * The Knowledge Inscriber's menu: the knowledge core, the core's stored patterns mirrored across the 7x3
 * read-only grid, the 3x3 ghost grid the player edits to say which recipe to encode, and the recipe's
 * result beside it.
 *
 * <p>There is no pattern slot - nothing produces a pattern here; the core is the pattern store, see
 * {@code BlockEntityKnowledgeInscriber}.
 */
public class MenuKnowledgeInscriber extends AbstractContainerMenu {

    /**
     * Fallback geometry, copied from the reference container.
     *
     * <p>These describe the wells' interiors, not the frames; 'correcting' them by a pixel puts the item
     * on the frame. Suspected of being off twice, and both times the suspicion was wrong.
     */
    private static final int FB_CORE_X = 186;
    private static final int FB_CORE_Y = 8;
    private static final int FB_PATTERN_X = 26;
    private static final int FB_PATTERN_Y = 18;
    private static final int FB_CRAFT_X = 26;
    private static final int FB_CRAFT_Y = 90;
    private static final int FB_RESULT_X = 116;
    private static final int FB_RESULT_Y = 108;
    private static final int FB_INV_X = 8;
    private static final int FB_INV_Y = 160;
    private static final int FB_HOTBAR_Y = 218;
    private static final int PITCH = 18;
    private static final int PATTERN_COLS = 7;
    private static final int PATTERN_ROWS = 3;
    private static final int PATTERN_COUNT = PATTERN_COLS * PATTERN_ROWS;
    private static final int CRAFT_SIZE = 9;

    /** Player inventory comes first, as everywhere else in this mod. */
    private static final int PLAYER_SLOTS = 36;
    private static final int IDX_CORE = PLAYER_SLOTS;
    private static final int IDX_PATTERN_START = IDX_CORE + 1;
    private static final int IDX_CRAFT_START = IDX_PATTERN_START + PATTERN_COUNT;
    private static final int IDX_RESULT = IDX_CRAFT_START + CRAFT_SIZE;

    /**
     * Vanilla's menu-button packet carries only an id, which is why the delete flag rides in it: the client
     * cannot name a pattern index on the server, and it does not need to.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        runButton(player, id == 1);
        return true;
    }

    /** Slot indices inside the block's own container. */
    private static final int MACHINE_CORE = 0;

    /** Grid the stored patterns are laid out on. */
    public static final int PATTERN_SLOTS = PATTERN_COUNT;
    public static final int CRAFT_SLOTS = CRAFT_SIZE;

    /**
     * Menu index of one cell of the player-editable grid. Public because JEI names these slots too, for
     * drag drop areas and for the recipe transfer's slot list.
     */
    public static int gridSlotIndex(int cell) {
        return IDX_CRAFT_START + cell;
    }

    private final @Nullable BlockEntityKnowledgeInscriber inscriber;

    /**
     * The machine's own slots, unindexed. Held separately so button state can be read from the slots
     * directly: the core is a real slot, so {@code AbstractContainerMenu} already keeps it identical on
     * both sides, and each side has the recipe manager locally.
     */
    private final Container machine;

    /**
     * The player's inventory, kept for its {@code player} - the client menu has no block entity to ask for
     * a level, and the recipe manager the preview needs hangs off the player's own level.
     */
    private final Inventory playerInventory;

    /**
     * The result well's container.
     *
     * <p>The client's own, deliberately: the preview is worked out from the grid the player is looking at,
     * on the side looking at it, where a server-owned container would show the result a round trip late -
     * and nothing at all if the two sides disagreed. Storing the recipe happens on the server; this is
     * only a picture of what the player has built.
     */
    private final SimpleContainer previewResult = new SimpleContainer(1);

    /**
     * The 7x3 wells' container, on the client.
     *
     * <p>Client-owned, like {@link #previewResult}: the wells are derived from the knowledge core item,
     * and the core is a slot, so the client can work them out itself. The machine's own container has
     * slots in the same places for the server, but this block has no update tag to sync with.
     */
    private final SimpleContainer mirrorDisplay =
            new SimpleContainer(BlockEntityKnowledgeInscriber.MIRROR_SLOT_COUNT);

    /** Signature of the core the wells were last filled from, so they are only refilled when it changes. */
    private int mirroredCore = -1;

    /**
     * The core signature as of {@link #coreSignatureTick}: hashing a core walks its whole stored pattern
     * store and the screen asks once a frame, so it is taken at most once a tick - one tick of staleness
     * changes nothing, because the screen polls its own state once a tick anyway.
     */
    private int sampledCore = -1;
    private long coreSignatureTick = Long.MIN_VALUE;

    /** Last grid {@link #updatePreview} resolved, so it only resolves when the grid changes. */
    private int previewedSignature = -1;

    /** The grid signature as of {@link #gridSignatureTick}, sampled once a tick for the same reason. */
    private int sampledGrid = -1;
    private long gridSignatureTick = Long.MIN_VALUE;

    /** The nine grid cells, in one list reused by {@link #gridSignature} instead of a fresh one per call. */
    private final List<ItemStack> gridScratch = new ArrayList<>(CRAFT_SIZE);

    // Synced to the client through the menu's data slots, as the Arcane Assembler's status is.
    public static final int DATA_HAS_CORE = 0;
    public static final int DATA_STATE = 1;
    private static final int DATA_SIZE = 2;

    private final int[] clientData = new int[DATA_SIZE];
    private final ContainerData data;

    /** Client constructor: the block entity lives on the client already, so nothing is wired here. */
    public MenuKnowledgeInscriber(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, (BlockEntityKnowledgeInscriber) null);
    }

    /** Server constructor, opened from the block. */
    public MenuKnowledgeInscriber(
            int containerId, Inventory playerInventory, @Nullable BlockEntityKnowledgeInscriber inscriber) {
        super(ModMenuTypes.KNOWLEDGE_INSCRIBER.get(), containerId);
        this.inscriber = inscriber;
        this.playerInventory = playerInventory;

        // Sized to the block's whole slot list: slots are matched by index on both sides.
        Container machine = inscriber == null
                ? new SimpleContainer(BlockEntityKnowledgeInscriber.SLOT_COUNT)
                : inscriber.getInventory();
        this.machine = machine;

        // 1. Player inventory, three rows then the hotbar.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, FB_INV_X + col * PITCH, FB_INV_Y + row * PITCH));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, FB_INV_X + col * PITCH, FB_HOTBAR_Y));
        }

        // 2. Knowledge core. The only machine slot that holds an item.
        addSlot(new Slot(machine, MACHINE_CORE, FB_CORE_X, FB_CORE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.KNOWLEDGE_CORE.get());
            }
        });

        // 3. The core's stored patterns, read-only, derived from the core item - see refreshMirrors.
        for (int i = 0; i < PATTERN_COUNT; i++) {
            addSlot(new ReadOnlySlot(
                    inscriber == null ? mirrorDisplay : machine,
                    inscriber == null ? i : BlockEntityKnowledgeInscriber.MIRROR_SLOT_START + i,
                    FB_PATTERN_X + (i % PATTERN_COLS) * PITCH,
                    FB_PATTERN_Y + (i / PATTERN_COLS) * PITCH));
        }

        // 4. The 3x3 recipe to encode: a ghost grid on the client, the machine's own container on the
        // server. See GhostGridSlot for why the write is a payload rather than a slot sync.
        for (int i = 0; i < CRAFT_SIZE; i++) {
            int x = FB_CRAFT_X + (i % 3) * PITCH;
            int y = FB_CRAFT_Y + (i / 3) * PITCH;
            addSlot(inscriber == null
                    ? new GhostGridSlot(machine, BlockEntityKnowledgeInscriber.GRID_SLOT_START + i, x, y, containerId)
                    : new MachineGridSlot(machine, BlockEntityKnowledgeInscriber.GRID_SLOT_START + i, x, y));
        }

        // 5. The result well. The machine's own container, so vanilla syncs what the server resolved;
        // the client resolving for itself drew nothing. Only this well, not the player's input grid.
        addSlot(new ReadOnlySlot(previewResult, 0, FB_RESULT_X, FB_RESULT_Y));

        // 6. The button's inputs, reported to the client through the menu's data slots. Resolving a
        // recipe scans every recipe in the manager, so this is throttled to one recompute per tick.
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (inscriber == null) {
                    // Client: the synced values, written by set().
                    return index >= 0 && index < clientData.length ? clientData[index] : 0;
                }
                return switch (index) {
                    case DATA_HAS_CORE -> slotStack(IDX_CORE).is(ModItems.KNOWLEDGE_CORE.get()) ? 1 : 0;
                    // ACTIONABLE means the machine could store this, not that this player may: the recipe can be
            // gated by research. Pushed as Locked so the button says why instead of doing nothing.
            case DATA_STATE -> inscriber != null
                            && inscriber.status() == BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE
                            && !inscriber.canStore(playerInventory.player)
                    ? BlockEntityKnowledgeInscriber.STATUS_RESEARCH_LOCKED
                    : inscriber.status();
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                if (index >= 0 && index < clientData.length) {
                    clientData[index] = value;
                }
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
        addDataSlots(data);
    }

    /**
     * Handles clicks on the grid and on the stored patterns.
     *
     * <p>The carried stack is the instruction and the slot is only the target; an empty hand clears the
     * cell, anything else writes one of it. Vanilla's click reads what is <em>in</em> the slot instead,
     * which on a ghost grid took the ingredient out and made a placed recipe vanish on the second click.
     */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            int cell = slotId - IDX_CRAFT_START;
            if (cell >= 0 && cell < CRAFT_SIZE && clickType == ClickType.PICKUP) {
                ItemStack carried = getCarried();
                setGridCell(cell, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
                return;
            }
            int pattern = slotId - IDX_PATTERN_START;
            if (pattern >= 0 && pattern < PATTERN_COUNT) {
                loadStoredPattern(pattern);
                return;
            }
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    /**
     * Reads a stored pattern back onto the grid. The button acts on the grid, so this is also how a
     * recipe is deleted. The tail is cleared because a shapeless recipe's stored grid is a compact list
     * of its ingredients, and the grid that comes out has to resolve back to the same recipe.
     */
    private void loadStoredPattern(int index) {
        List<ItemStack> cells = storedGrid(index);
        if (cells == null) {
            // Says which well was asked for, so an empty well is distinguishable from the wrong one.
            ThaumicEnergistics.LOG.info("[inscriber] pattern well {} holds nothing to load", index);
            return;
        }
        ThaumicEnergistics.LOG.info(
                "[inscriber] loading pattern well {} -> {} ({} cells)",
                index,
                cells.isEmpty() ? "empty grid" : cells.getFirst(),
                cells.size());
        // One replacement, so the grid never holds a mixture of the old recipe and the new.
        fillGridFromRecipe(cells);
    }

    /**
     * The grid of the stored pattern in a given well, or {@code null} when the well is empty.
     *
     * <p><b>Read from the core by position, not from the well's slot.</b> The wells are read-only and
     * their backing mirror slots are never filled, so the slot gave the server a stale stack and the two
     * sides disagreed about the same click. The core's pattern list is in the order the wells are drawn.
     */
    private @Nullable List<ItemStack> storedGrid(int index) {
        if (index < 0) {
            return null;
        }
        HandlerKnowledgeCore core = handler();
        if (core == null) {
            return null;
        }
        List<ThEArcanePattern> patterns = core.patterns();
        return index < patterns.size() ? patterns.get(index).grid() : null;
    }

    /**
     * The server writes the container directly; the client goes through the slot, which is what sends the
     * payload. See {@code GhostGridSlot}.
     */
    private void setGridCell(int cell, ItemStack stack) {
        if (inscriber != null) {
            inscriber.setGridCell(cell, stack);
            return;
        }
        slots.get(IDX_CRAFT_START + cell).set(stack);
    }

    /**
     * Applies one grid cell, from {@code InscriberGridPayload}. Server only: on the client this writes
     * into a container the server never sees, which is what a JEI recipe transfer used to do - the grid
     * stayed empty and the button read 'Invalid'. The client's route into the grid is the slot, see
     * {@link #fillGridFromRecipe}.
     */
    public void setGridCell(Player player, int cell, ItemStack stack) {
        if (inscriber == null) {
            return;
        }
        inscriber.setGridCell(cell, stack);
        broadcastChanges();
    }

    /**
     * Applies a whole grid, from {@code InscriberGridFillPayload}. Server only, like {@link #setGridCell}:
     * one message in, one write, one resolution, so the two sides change together rather than a cell at a
     * time.
     *
     * @param cells the stacks sent; missing entries are treated as empty
     * @param count how many cells the grid has, so a short or long list cannot run past it
     */
    public void applyGridFill(Player player, List<ItemStack> cells, int count) {
        if (inscriber == null) {
            return;
        }
        List<ItemStack> full = new java.util.ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            full.add(i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
        }
        inscriber.setGrid(full);
        // The client's own copy was already written; the data slots have to catch up this tick.
        broadcastChanges();
    }

    /**
     * Whether the player is carrying a stack the given stack would match. For JEI, so an ingredient that
     * accepts several items is placed as one the player actually has.
     */
    public boolean playerHas(ItemStack wanted) {
        if (wanted.isEmpty()) {
            return false;
        }
        for (int i = 0; i < PLAYER_SLOTS; i++) {
            ItemStack stack = slotStack(i);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, wanted)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replaces the grid with a recipe's layout, the way a JEI transfer and a stored-pattern click both do.
     *
     * <p><b>One replacement, not nine writes.</b> Writing each cell through its slot sent a payload per
     * cell, so the server re-resolved nine times against grids that were part the old recipe and part the
     * new.
     *
     * <p>The write goes to the container rather than to the slots: {@code GhostGridSlot} sends a payload
     * when the <em>player</em> edits one cell, and this is not a player edit.
     */
    public void fillGridFromRecipe(List<ItemStack> cells) {
        List<ItemStack> full = new java.util.ArrayList<>(CRAFT_SIZE);
        for (int cell = 0; cell < CRAFT_SIZE; cell++) {
            ItemStack stack = cell < cells.size() ? cells.get(cell) : ItemStack.EMPTY;
            full.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
        }

        // 1. This side's grid, in one pass. Writing the container sends no payload.
        for (int cell = 0; cell < CRAFT_SIZE; cell++) {
            machine.setItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell, full.get(cell));
        }

        // 2. Tell the other side, once. The server has the block entity; the client sends the lot.
        if (inscriber != null) {
            inscriber.setGrid(full);
        } else {
            PacketDistributor.sendToServer(new thaumicenergistics.network.InscriberGridFillPayload(
                    containerId, full));
        }

        // 3. Show the new result now rather than on the next frame's poll.
        updatePreview();
    }

    /**
     * Works out what the grid the player has built stands for, and shows its result. Runs on both sides
     * because the client draws the result well, from its own copy of the ghost grid. Only when the grid
     * changes - resolving walks every arcane recipe, and this runs once a frame.
     */
    public void updatePreview() {
        int signature = gridSignature();
        if (signature == previewedSignature) {
            return;
        }
        previewedSignature = signature;
        Level level = level();
        List<ItemStack> cells = gridCells();
        if (level == null || ThEArcanePattern.isGridEmpty(cells)) {
            previewResult.setItem(0, ItemStack.EMPTY);
            return;
        }
        ThEArcanePattern pattern = ThEArcanePattern.resolveGrid(level, cells);
        previewResult.setItem(0, pattern == null ? ItemStack.EMPTY : pattern.result());
    }

    /** True when a knowledge core is in its slot, as the client last heard. */
    public boolean hasCore() {
        return data.get(DATA_HAS_CORE) != 0;
    }

    /**
     * Fills the 7x3 wells from the knowledge core. Called every frame, but does nothing unless the core
     * changed. Derived rather than remembered, because the wells are read-only slots and only the side
     * drawing them can write what they show.
     */
    public void refreshMirrors() {
        ItemStack core = slotStack(IDX_CORE);
        // An int key, not a string: the old getItem() + '|' + getComponentsPatch() called toString on
        // every component, re-serialising the core's whole stored pattern list sixty times a second.
        // Still taken at most once a tick: this is a per-frame call and the hash walks the whole store.
        long now = playerInventory.player.level().getGameTime();
        if (now != coreSignatureTick) {
            coreSignatureTick = now;
            sampledCore = StackSignatures.of(core);
        }
        if (sampledCore == mirroredCore) {
            return;
        }
        mirroredCore = sampledCore;
        HandlerKnowledgeCore handler = handler();
        List<ItemStack> outputs = handler == null ? List.of() : handler.storedOutputs();
        for (int i = 0; i < PATTERN_COUNT; i++) {
            ItemStack wanted = i < outputs.size() ? outputs.get(i) : ItemStack.EMPTY;
            if (!ItemStack.matches(mirrorDisplay.getItem(i), wanted)) {
                mirrorDisplay.setItem(i, wanted.copy());
            }
        }
    }

    private @Nullable HandlerKnowledgeCore handler() {
        Level level = level();
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(slotStack(IDX_CORE), level.registryAccess());
    }

    public boolean isGridEmpty() {
        return ThEArcanePattern.isGridEmpty(gridCells());
    }

    /**
     * Whether this menu could encode a recipe at all, ignoring which recipe it is. For JEI. The client
     * asks the synced data slot rather than the slot itself: its own copy of the core slot is not
     * reliably filled, which made an earlier button stick on 'No Core'.
     */
    public boolean canEncode() {
        if (inscriber == null) {
            return hasCore();
        }
        return slotStack(IDX_CORE).is(ModItems.KNOWLEDGE_CORE.get());
    }

    private List<ItemStack> gridCells() {
        List<ItemStack> cells = new ArrayList<>(CRAFT_SIZE);
        for (int i = 0; i < CRAFT_SIZE; i++) {
            cells.add(slotStack(IDX_CRAFT_START + i));
        }
        return cells;
    }

    /**
     * Signature of what the result well depends on, so it is only recomputed when it changed. An int
     * rather than a string: this runs once a frame and the string version serialised all nine cells'
     * patches every time. See {@link StackSignatures}.
     *
     * <p>Sampled at most once a tick, like the core's - one tick of staleness changes nothing, because the
     * screen polls its own state once a tick anyway - and filled into {@link #gridScratch}, one list for the
     * menu's life, where this used to allocate a fresh nine-element ArrayList on every call.
     */
    private int gridSignature() {
        long now = playerInventory.player.level().getGameTime();
        if (now != gridSignatureTick) {
            gridSignatureTick = now;
            gridScratch.clear();
            for (int i = 0; i < CRAFT_SIZE; i++) {
                gridScratch.add(slotStack(IDX_CRAFT_START + i));
            }
            sampledGrid = StackSignatures.of(gridScratch);
        }
        return sampledGrid;
    }

    /**
     * What the button should say, and whether it is usable. Driven by container data because the
     * client's copy of this menu never received the core slot's contents, so a decision taken from the
     * slots was made against an empty container.
     */
    public int buttonState() {
        if (data.get(DATA_HAS_CORE) == 0) {
            return BlockEntityKnowledgeInscriber.STATUS_READY;
        }
        return data.get(DATA_STATE);
    }

    /**
     * True when pressing the button would remove a stored recipe rather than store one. Deliberately not
     * a mode the player can put the button into: a grid that resolves to nothing is Invalid, however
     * many patterns the core holds.
     */
    public boolean isDelete() {
        return data.get(DATA_HAS_CORE) != 0
                && data.get(DATA_STATE) == BlockEntityKnowledgeInscriber.STATUS_ALREADY_STORED;
    }

    /**
     * True when pressing the button would do something, either way round. Delete counts.
     */
    public boolean isActionable() {
        if (isDelete()) {
            return true;
        }
        return data.get(DATA_HAS_CORE) != 0
                && data.get(DATA_STATE) == BlockEntityKnowledgeInscriber.STATUS_ACTIONABLE;
    }

    /**
     * The level to resolve recipes against: the block entity's on the server, the player's own on the
     * client. The client menu is built without a block entity, so asking one there returns null and the
     * result well could never be filled.
     */
    private @Nullable Level level() {
        if (inscriber != null) {
            return inscriber.getLevel();
        }
        return playerInventory.player.level();
    }

    private ItemStack slotStack(int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        return slots.get(index).getItem();
    }

    /**
     * Runs the button on the server, from the menu-button packet, so the research check and the item
     * write happen where they can be trusted.
     */
    public void runButton(Player player, boolean delete) {
        if (inscriber == null) {
            return;
        }
        if (delete) {
            inscriber.deleteStored(player);
        } else {
            inscriber.save(player);
        }
        // A save clears the grid, so the cached resolution must catch up before the status is pushed.
        inscriber.refreshResolution();
        broadcastChanges();
        updatePreview();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index < PLAYER_SLOTS) {
            // The core is the only machine slot that takes an item, so shift-clicking has one target.
            if (!moveItemStackTo(stack, IDX_CORE, IDX_CORE + 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, PLAYER_SLOTS, true)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (inscriber == null) {
            return true;
        }
        var level = inscriber.getLevel();
        var pos = inscriber.getBlockPos();
        return level != null
                && level.getBlockEntity(pos) == inscriber
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /** The block this menu drives, or {@code null} on the client. */
    public @Nullable BlockEntityKnowledgeInscriber inscriber() {
        return inscriber;
    }
}
