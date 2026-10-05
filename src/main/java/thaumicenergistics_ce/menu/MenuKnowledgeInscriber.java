package thaumicenergistics_ce.menu;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModItems;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.menu.slot.GhostGridSlot;
import thaumicenergistics_ce.menu.slot.MachineGridSlot;
import thaumicenergistics_ce.menu.slot.ReadOnlySlot;
import thaumicenergistics_ce.network.KnowledgeInscriberReceiver;

/**
 * The Knowledge Inscriber's menu: the core slot, the 7x3 read-only grid of patterns, the player's
 * 3x3 ghost grid and the result well.
 * <ul>
 *   <li>No output slot: the core is the pattern store, see {@code BlockEntityKnowledgeInscriber}.
 * </ul>
 */
public class MenuKnowledgeInscriber extends AbstractContainerMenu implements KnowledgeInscriberReceiver {

    /** Geometry from the reference container: a well's interior, not its frame. */
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

    private static final int PLAYER_SLOTS = 36;

    /** Package-private for the readout and the preview, which both ask what the core slot holds. */
    static final int IDX_CORE = PLAYER_SLOTS;

    private static final int IDX_PATTERN_START = IDX_CORE + 1;

    /** Package-private for the grid state, whose window into the slots is the 3x3 recipe grid. */
    static final int IDX_CRAFT_START = IDX_PATTERN_START + PATTERN_COUNT;

    /** The menu-button packet carries only an id, so the delete flag rides in it. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        runButton(player, id == 1);
        return true;
    }

    private static final int MACHINE_CORE = 0;

    public static final int PATTERN_SLOTS = PATTERN_COUNT;
    public static final int CRAFT_SLOTS = CRAFT_SIZE;

    /** Menu index of one cell of the player grid; public because JEI names these slots too. */
    public static int gridSlotIndex(int cell) {
        return IDX_CRAFT_START + cell;
    }

    /** Package-private for the readout, whose core check runs on the side the machine is present. */
    final @Nullable BlockEntityKnowledgeInscriber inscriber;

    /** Package-private for the grid state, which writes a whole recipe into it in one pass. */
    final Container machine;

    final Inventory playerInventory;

    private final InscriberGridState grid;

    private final InscriberPreview preview;

    private final InscriberMenuReadout readout;

    public static final int DATA_HAS_CORE = 0;
    public static final int DATA_STATE = 1;

    public MenuKnowledgeInscriber(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, (BlockEntityKnowledgeInscriber) null);
    }

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

        this.grid = new InscriberGridState(this);
        this.preview = new InscriberPreview(this, grid);

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
                    inscriber == null ? preview.mirrors() : machine,
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
                    ? new GhostGridSlot(
                            machine,
                            BlockEntityKnowledgeInscriber.GRID_SLOT_START + i,
                            x,
                            y,
                            (cell, stack) -> MenuNetwork.sendInscriberGrid(containerId, cell, stack))
                    : new MachineGridSlot(machine, BlockEntityKnowledgeInscriber.GRID_SLOT_START + i, x, y));
        }

        // 5. The result well. The machine's own container, so vanilla syncs what the server resolved;
        // the client resolving for itself drew nothing. Only this well, not the player's input grid.
        addSlot(new ReadOnlySlot(preview.well(), 0, FB_RESULT_X, FB_RESULT_Y));

        // 6. The button's inputs, reported to the client through the menu's data slots. Resolving a
        // recipe scans every recipe in the manager, so this is throttled to one recompute per tick.
        this.readout = new InscriberMenuReadout(this, playerInventory);
        addDataSlots(readout.data());
    }

    /**
     * Vanilla reads what is <em>in</em> the clicked slot, which lost a placed recipe on the second
     * click; here the carried stack instructs and the slot is only the target.
     */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < slots.size()) {
            int cell = slotId - IDX_CRAFT_START;
            if (cell >= 0 && cell < CRAFT_SIZE && clickType == ClickType.PICKUP) {
                ItemStack carried = getCarried();
                grid.setCell(cell, carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
                return;
            }
            int pattern = slotId - IDX_PATTERN_START;
            if (pattern >= 0 && pattern < PATTERN_COUNT) {
                grid.loadPattern(pattern);
                return;
            }
        }
        super.clicked(slotId, dragType, clickType, player);
    }

    @Override
    public int gridSlotStart() {
        return BlockEntityKnowledgeInscriber.GRID_SLOT_START;
    }

    @Override
    public int gridSlotCount() {
        return BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT;
    }

    /**
     * Applies one grid cell from {@code InscriberGridPayload}, server only: a client write lands in a
     * container the server never sees.
     */
    @Override
    public void setGridCell(Player player, int cell, ItemStack stack) {
        if (inscriber == null) {
            return;
        }
        inscriber.setGridCell(cell, stack);
        broadcastChanges();
    }

    /**
     * Applies a whole grid from {@code InscriberGridFillPayload}, server only: one write and one
     * resolution, so the two sides change together rather than a cell at a time.
     * @param cells the stacks sent; missing entries are treated as empty
     * @param count how many cells the grid has, so a short or long list cannot run past it
     */
    @Override
    public void applyGridFill(Player player, List<ItemStack> cells, int count) {
        if (inscriber == null) {
            return;
        }
        List<ItemStack> full = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            full.add(i < cells.size() ? cells.get(i) : ItemStack.EMPTY);
        }
        inscriber.setGrid(full);
        // The client's own copy was already written; the data slots have to catch up this tick.
        broadcastChanges();
    }

    @Override
    public int containerId() {
        return containerId;
    }

    /** Whether the player carries a stack that matches: JEI places what the player actually has. */
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
     * Fills the grid from a recipe's layout, as a JEI transfer and a pattern click do, in one write: a
     * payload per cell made the server re-resolve against a grid that was half the old recipe.
     */
    public void fillGridFromRecipe(List<ItemStack> cells) {
        grid.fillFromRecipe(cells);
        preview.update();
    }

    public void updatePreview() {
        preview.update();
    }

    public boolean hasCore() {
        return readout.hasCore();
    }

    /**
     * Fills the 7x3 wells from the core, each frame but only on a change: they are read-only slots, so
     * only the side drawing them can write what they show.
     */
    public void refreshMirrors() {
        preview.refreshMirrors();
    }

    /** Package-private for the grid state and the preview, whose reads all start at the core item. */
    @Nullable HandlerKnowledgeCore handler() {
        Level level = level();
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(slotStack(IDX_CORE), level.registryAccess());
    }

    public boolean isGridEmpty() {
        return ThEArcanePattern.isGridEmpty(grid.cells());
    }

    /**
     * Whether the menu could encode at all, whatever the recipe. The client reads the synced data slot,
     * since its own slot copy is not reliably filled.
     */
    public boolean canEncode() {
        return readout.canEncode();
    }

    public int buttonState() {
        return readout.buttonState();
    }

    /**
     * True when the button would delete rather than store, not a player-picked mode: a grid resolving to
     * nothing is Invalid however many patterns the core holds.
     */
    public boolean isDelete() {
        return readout.isDelete();
    }

    public boolean isActionable() {
        return readout.isActionable();
    }

    /** Package-private for the grid state and the preview, which resolve against the same level. */
    @Nullable Level level() {
        if (inscriber != null) {
            return inscriber.getLevel();
        }
        return playerInventory.player.level();
    }

    /** Package-private for the three collaborators, whose reads are all slot reads. */
    ItemStack slotStack(int index) {
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
        preview.update();
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

    public @Nullable BlockEntityKnowledgeInscriber inscriber() {
        return inscriber;
    }
}
