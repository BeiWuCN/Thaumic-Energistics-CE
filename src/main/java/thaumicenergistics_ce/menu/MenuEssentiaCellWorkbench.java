package thaumicenergistics_ce.menu;

import appeng.api.stacks.AEKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.CellPartitionSlot;
import appeng.menu.slot.IPartitionSlotHost;
import appeng.menu.slot.RestrictedInputSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.item.ItemEssentiaCell;
import thaumicenergistics_ce.network.PartitionWellReceiver;

/**
 * The Essentia Cell Workbench's menu: the cell, its upgrade slots, and the partition being edited.
 * It is an AE2 menu, so the upgrades panel, the cell slot and the wells come with AE2's own
 * handling. The partition grid is 63 wells and a mark arrives as PartitionWellPayload; what the
 * wells hold belongs to the {@link CellPartitionEditor}, while this menu holds the slots.
 */
public class MenuEssentiaCellWorkbench extends UpgradeableMenu<BlockEntityEssentiaCellWorkbench>
        implements PartitionWellReceiver, IPartitionSlotHost {

    /** Client action: fill the wells from what the cell already holds. */
    private static final String ACTION_PARTITION = "partition";

    /** Client action: empty every well. */
    private static final String ACTION_CLEAR = "clear";

    // The cell sits top right, where the art draws it; the wells and the inventory are the style's job.
    private static final int CELL_X = 152;
    private static final int CELL_Y = 8;

    // Package-private for the partition editor, which writes the cell and tells the host it changed.
    final BlockEntityEssentiaCellWorkbench workbench;

    // Built by setupConfig, which AE2's base calls while it constructs, so the field cannot be final.
    private CellPartitionEditor partitionEditor;

    // Built in the constructor body: the ranges it measures need the slots AE2 has already filed.
    private final CellWorkbenchShiftClick shiftClick;

    // The wells ask this menu whether they are enabled, so the slot is kept; setupInventorySlots sets it.
    private Slot cellSlot;

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, hostFrom(playerInventory, buf));
    }

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, @Nullable BlockEntityEssentiaCellWorkbench workbench) {
        // AE2's base calls the three setup methods from its own constructor, so they read the host.
        super(ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), containerId, playerInventory, host(workbench));
        this.workbench = getHost();
        this.shiftClick = new CellWorkbenchShiftClick(this);
        registerClientAction(ACTION_PARTITION, this::partitionToContents);
        registerClientAction(ACTION_CLEAR, this::clearPartition);
    }

    @Override
    protected void setupInventorySlots() {
        Slot cell = new Slot(getHost().getInventory(), BlockEntityEssentiaCellWorkbench.CELL_SLOT, CELL_X, CELL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ItemEssentiaCell;
            }

            @Override
            public int getMaxStackSize() {
                // One cell per slot: a cell carries its contents in its own stack, so a pile would share one.
                return 1;
            }
        };
        this.cellSlot = cell;
        addSlot(cell, SlotSemantics.STORAGE_CELL);
    }

    @Override
    protected void setupConfig() {
        this.partitionEditor =
                new CellPartitionEditor(this, getHost().getPartition().createMenuWrapper());
        for (int well = 0; well < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; well++) {
            // AE2's own partition slot, so a well with no cell behind it draws itself faint and empty.
            addSlot(new CellPartitionSlot(partitionEditor.partition(), this, well), SlotSemantics.CONFIG);
        }
    }

    /**
     * AE2 shows the upgrade panel - frame, icons and tooltip - only while a card slot reports enabled,
     * so the cards ride on the cell: same slots AE2 builds, only the enabled check differs.
     */
    @Override
    protected void setupUpgrades() {
        IUpgradeInventory upgrades = getUpgrades();
        for (int index = 0; index < upgrades.size(); index++) {
            RestrictedInputSlot slot = new RestrictedInputSlot(
                    RestrictedInputSlot.PlacableItemType.UPGRADES, upgrades, index) {
                @Override
                public boolean isSlotEnabled() {
                    return hasCellInMenu();
                }
            };
            slot.setNotDraggable();
            addSlot(slot, SlotSemantics.UPGRADE);
        }
    }

    /**
     * Whether a cell sits in the menu's own slot. The wells and the card slots both follow the slot rather
     * than the block entity: on a client the host may be a stand-in, and only the slot is synced.
     */
    boolean hasCellInMenu() {
        return cellSlot != null && cellSlot.getItem().getItem() instanceof ItemEssentiaCell;
    }

    /** The cell's slot, where the one cell goes; the shift-click collaborator names its range. */
    Slot cellSlot() {
        return getSlots(SlotSemantics.STORAGE_CELL).get(0);
    }

    /**
     * AE2 asks this to draw a well and to let it be clicked: with no cell there is nothing to mark, so the
     * wells go faint and empty.
     */
    @Override
    public boolean isPartitionSlotEnabled(int well) {
        return hasCellInMenu();
    }

    public static int partitionSlotCount() {
        return BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS;
    }

    public int partitionSlotIndex(int well) {
        return slots.indexOf(getSlots(SlotSemantics.CONFIG).get(well));
    }

    /** The well a slot is, or {@code -1} when the slot is not one of the partition's. */
    public int wellOf(Slot slot) {
        return getSlots(SlotSemantics.CONFIG).indexOf(slot);
    }

    public boolean hasCell() {
        return workbench.hasCell();
    }

    public @Nullable AEKey keyInWell(int well) {
        return partitionEditor.keyInWell(well);
    }

    /**
     * Fills every well from the aspects the cell already holds. Sent from the client: the block entity,
     * which owns the cell, does the write on the server.
     */
    public void partitionToContents() {
        if (isClientSide()) {
            sendClientAction(ACTION_PARTITION);
            return;
        }
        partitionEditor.partitionToContents();
    }

    /** Empties every well. Sent from the client, like {@link #partitionToContents}. */
    public void clearPartition() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }
        partitionEditor.clearPartition();
    }

    /**
     * Applies a well edit that arrived from a client, the only route that reaches the server: a mark, or
     * {@code PartitionWellPayload.CLEAR} to take one out. Refused without a cell, which holds the partition.
     */
    @Override
    public void setPartitionWell(
            int well,
            ResourceLocation aspectId,
            Player player) {
        partitionEditor.setWell(well, aspectId, player);
    }

    @Override
    public int containerId() {
        return containerId;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        CellWorkbenchShiftClick.Move move = shiftClick.moveFor(slot, index, stack);
        if (move == null || !moveItemStackTo(stack, move.from(), move.to(), move.reverse())) {
            // Nothing to hand the stack to, or the destination refused it: it stays where it is.
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
        var level = workbench.getLevel();
        if (level == null) {
            // The stand-in that serves a client with no block entity in reach: nothing to validate against.
            return true;
        }
        var pos = workbench.getBlockPos();
        return level.getBlockEntity(pos) == workbench
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /**
     * The block entity the server opened, or on the client the one the opening packet names; a stand-in
     * follows when neither is there, because AE2's base reads the host while it builds the slots.
     */
    private static BlockEntityEssentiaCellWorkbench hostFrom(
            Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        if (buf.readableBytes() < Long.BYTES) {
            return host(null);
        }
        BlockPos pos = buf.readBlockPos();
        if (playerInventory.player.level().getBlockEntity(pos) instanceof BlockEntityEssentiaCellWorkbench found) {
            return found;
        }
        return standIn(pos);
    }

    private static BlockEntityEssentiaCellWorkbench host(@Nullable BlockEntityEssentiaCellWorkbench workbench) {
        return workbench != null ? workbench : standIn(BlockPos.ZERO);
    }

    private static BlockEntityEssentiaCellWorkbench standIn(BlockPos pos) {
        return new BlockEntityEssentiaCellWorkbench(
                pos, ModBlocks.ESSENTIA_CELL_WORKBENCH.get().defaultBlockState());
    }
}
