package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.network.KnowledgeInscriberReceiver;

/**
 * The Knowledge Inscriber's menu: the core slot, the 7x3 read-only grid of patterns, the player's
 * 3x3 ghost grid and the result well.
 * There is no output slot, because the core is the pattern store; see
 * {@code BlockEntityKnowledgeInscriber}.
 */
public class MenuKnowledgeInscriber extends AbstractContainerMenu implements KnowledgeInscriberReceiver {

    /** Package-private for the layout, which turns a well index into a column and a row. */
    static final int PATTERN_COLS = 7;
    private static final int PATTERN_ROWS = 3;
    private static final int PATTERN_COUNT = PATTERN_COLS * PATTERN_ROWS;
    private static final int CRAFT_SIZE = 9;

    /** Package-private for the layout, whose player band is the first of the menu's slots. */
    static final int PLAYER_SLOTS = 36;

    /** Package-private for the readout and the preview, which both ask what the core slot holds. */
    static final int IDX_CORE = PLAYER_SLOTS;

    /** Package-private for the click routing, which reads a well's index off the slot id. */
    static final int IDX_PATTERN_START = IDX_CORE + 1;

    /** Package-private for the grid state, whose window into the slots is the 3x3 recipe grid. */
    static final int IDX_CRAFT_START = IDX_PATTERN_START + PATTERN_COUNT;

    /** The menu-button packet carries only an id, so the delete flag rides in it. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        runButton(player, id == 1);
        return true;
    }

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

        this.machine = InscriberSlotLayout.machine(inscriber);

        this.grid = new InscriberGridState(this);
        this.preview = new InscriberPreview(this, grid);

        InscriberSlotLayout.addSlots(this, playerInventory, preview, machine, inscriber, this::addSlot);

        // 6. The button's inputs, reported to the client through the menu's data slots. Resolving a
        // recipe scans every recipe in the manager, so this is throttled to one recompute per tick.
        this.readout = new InscriberMenuReadout(this, playerInventory);
        addDataSlots(readout.data());
    }

    /**
     * Vanilla reads what is in the clicked slot, which lost a placed recipe on the second
     * click; here the carried stack instructs and the slot is only the target.
     */
    @Override
    public void clicked(int slotId, int dragType, ClickType clickType, Player player) {
        if (InscriberGridWrites.route(this, grid, slotId, clickType)) {
            return;
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

    @Override
    public void setGridCell(Player player, int cell, ItemStack stack) {
        InscriberGridWrites.setCell(this, cell, stack);
    }

    @Override
    public void applyGridFill(Player player, List<ItemStack> cells, int count) {
        InscriberGridWrites.fill(this, cells, count);
    }

    @Override
    public int containerId() {
        return containerId;
    }

    /** Whether the player carries a stack that matches: JEI places what the player actually has. */
    public boolean playerHas(ItemStack wanted) {
        return InscriberSlotLayout.playerHas(this, wanted);
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
        return InscriberMachineAccess.handler(this);
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
        return InscriberMachineAccess.level(this);
    }

    /** Package-private for the three collaborators, whose reads are all slot reads. */
    ItemStack slotStack(int index) {
        return InscriberMachineAccess.slotStack(this, index);
    }

    /**
     * Runs the button on the server, from the menu-button packet, so the research check and the item
     * write happen where they can be trusted.
     */
    public void runButton(Player player, boolean delete) {
        InscriberButtonAction.run(this, player, delete);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        InscriberSlotLayout.Move move = InscriberSlotLayout.moveFor(index);
        if (move == null || !moveItemStackTo(stack, move.from(), move.to(), move.reverse())) {
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
        return InscriberMachineAccess.stillValid(this, player);
    }

    public @Nullable BlockEntityKnowledgeInscriber inscriber() {
        return inscriber;
    }
}
