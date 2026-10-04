package thaumicenergistics_ce.menu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.item.ItemEssentiaCell;

/**
 * The Essentia Cell Workbench's menu: the cell, and the partition being edited.
 * <ul>
 *   <li>The partition grid is 63 wells, as the art draws and AE2's cell workbench uses.
 *   <li>The wells are AE2's {@link FakeSlot}: {@code AEKey}s edited by click or drag, nothing handed over.
 *   <li>The partition lives on the cell item; the block entity mirrors it into the wells and writes back.
 * </ul>
 */
public class MenuEssentiaCellWorkbench extends AbstractContainerMenu {

    public static final int IDX_CELL = 36;

    public static final int IDX_PARTITION_START = IDX_CELL + 1;

    private static final int PLAYER_SLOTS = 36;

    // The layout AE2's cell workbench uses and our art draws: cell top right, partition, inventory.
    private static final int CELL_X = 152;
    private static final int CELL_Y = 8;
    private static final int PARTITION_X = 8;
    private static final int PARTITION_Y = 29;
    private static final int PARTITION_COLS = 9;
    private static final int INV_X = 8;
    private static final int INV_Y = 167;
    private static final int HOTBAR_Y = 225;
    private static final int PITCH = 18;

    private final @Nullable BlockEntityEssentiaCellWorkbench workbench;

    private final Container cellContainer;

    private final ConfigMenuInventory partition;

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        // Picks the block-entity constructor: a bare null is ambiguous between two parameters.
        this(containerId, playerInventory, (BlockEntityEssentiaCellWorkbench) null);
    }

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, @Nullable BlockEntityEssentiaCellWorkbench workbench) {
        super(ModMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), containerId);
        this.workbench = workbench;

        this.cellContainer = workbench == null ? new SimpleContainer(1) : workbench.getInventory();
        ConfigInventory partitionSource = workbench == null
                ? ConfigInventory.configTypes(BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS).build()
                : workbench.getPartition();
        this.partition = partitionSource.createMenuWrapper();

        // 1. The player's inventory, first as everywhere else in this mod.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, INV_X + column * PITCH, INV_Y + row * PITCH));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, INV_X + column * PITCH, HOTBAR_Y));
        }

        // 2. The cell: only a storage cell goes here.
        addSlot(new Slot(cellContainer, 0, CELL_X, CELL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ItemEssentiaCell;
            }
        });

        // 3. The partition wells, positioned by hand: FakeSlot places the slot at 0,0 and nothing
        // repositions it here, so all 63 would stack. Writing x and y needs the access transformer.
        for (int index = 0; index < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; index++) {
            FakeSlot well = new FakeSlot(partition, index);
            well.x = PARTITION_X + (index % PARTITION_COLS) * PITCH;
            well.y = PARTITION_Y + (index / PARTITION_COLS) * PITCH;
            addSlot(well);
        }
    }

    public static int partitionSlotCount() {
        return BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS;
    }

    public static int partitionSlotIndex(int well) {
        return IDX_PARTITION_START + well;
    }

    public boolean hasCell() {
        if (workbench != null) {
            return workbench.hasCell();
        }
        return slotAt(IDX_CELL).getItem().getItem() instanceof ItemEssentiaCell;
    }

    public @Nullable AEKey keyInWell(int well) {
        GenericStack stack = partition.getDelegate().getStack(well);
        return stack == null ? null : stack.what();
    }

    public void partitionToContents() {
        if (workbench == null) {
            return;
        }
        ItemEssentiaCell.partitionToContents(workbench.getCell());
        workbench.setChanged();
        broadcastChanges();
    }

    public void clearPartition() {
        if (workbench == null) {
            return;
        }
        ItemEssentiaCell.clearPartition(workbench.getCell());
        workbench.setChanged();
        broadcastChanges();
    }

    private Slot slotAt(int index) {
        return slots.get(index);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == IDX_CELL) {
            if (!moveItemStackTo(stack, 0, PLAYER_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < PLAYER_SLOTS) {
            if (stack.getItem() instanceof ItemEssentiaCell && !slotAt(IDX_CELL).hasItem()) {
                if (!moveItemStackTo(stack, IDX_CELL, IDX_CELL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
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
        if (workbench == null) {
            return true;
        }
        var level = workbench.getLevel();
        var pos = workbench.getBlockPos();
        return level != null
                && level.getBlockEntity(pos) == workbench
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}
