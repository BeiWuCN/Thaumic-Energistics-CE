package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ThEArcanePattern;
import thaumicenergistics_ce.blockentity.inscriber.BlockEntityKnowledgeInscriber;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;
import thaumicenergistics_ce.network.KnowledgeInscriberReceiver;

public class MenuKnowledgeInscriber extends AbstractContainerMenu implements KnowledgeInscriberReceiver {

    static final int PATTERN_COLS = 7;
    private static final int PATTERN_ROWS = 3;
    private static final int PATTERN_COUNT = PATTERN_COLS * PATTERN_ROWS;
    private static final int CRAFT_SIZE = 9;

    static final int PLAYER_SLOTS = 36;

    static final int IDX_CORE = PLAYER_SLOTS;

    static final int IDX_PATTERN_START = IDX_CORE + 1;

    static final int IDX_CRAFT_START = IDX_PATTERN_START + PATTERN_COUNT;

    @Override
    public boolean clickMenuButton(Player player, int id) {
        runButton(player, id == 1);
        return true;
    }

    public static final int PATTERN_SLOTS = PATTERN_COUNT;
    public static final int CRAFT_SLOTS = CRAFT_SIZE;

    public static int gridSlotIndex(int cell) {
        return IDX_CRAFT_START + cell;
    }

    final @Nullable BlockEntityKnowledgeInscriber inscriber;

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

        this.readout = new InscriberMenuReadout(this, playerInventory);
        addDataSlots(readout.data());
    }

    @Override
    public void clicked(int slotId, int dragType, ContainerInput clickType, Player player) {
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

    public boolean playerHas(ItemStack wanted) {
        return InscriberSlotLayout.playerHas(this, wanted);
    }

    public void fillGridFromRecipe(List<ItemStack> cells) {
        grid.fillFromRecipe(cells);
        preview.update();
    }

    public void updatePreview() {
        preview.update();
    }

    @Override
    public void broadcastChanges() {
        updatePreview();
        super.broadcastChanges();
    }

    public boolean hasCore() {
        return readout.hasCore();
    }

    public void refreshMirrors() {
        preview.refreshMirrors();
    }

    @Nullable HandlerKnowledgeCore handler() {
        return InscriberMachineAccess.handler(this);
    }

    public boolean isGridEmpty() {
        return ThEArcanePattern.isGridEmpty(grid.cells());
    }

    public boolean canEncode() {
        return readout.canEncode();
    }

    public int buttonState() {
        return readout.buttonState();
    }

    public boolean isDelete() {
        return readout.isDelete();
    }

    public boolean isActionable() {
        return readout.isActionable();
    }

    @Nullable Level level() {
        return InscriberMachineAccess.level(this);
    }

    ItemStack slotStack(int index) {
        return InscriberMachineAccess.slotStack(this, index);
    }

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
