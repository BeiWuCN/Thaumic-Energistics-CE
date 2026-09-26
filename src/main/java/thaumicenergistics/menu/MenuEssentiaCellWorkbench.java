package thaumicenergistics.menu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigInventory;
import appeng.util.ConfigMenuInventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics.init.ModMenuTypes;
import thaumicenergistics.item.ItemEssentiaCell;

/**
 * The Essentia Cell Workbench's menu.
 *
 * <p>Two things on screen: the cell, and the partition being edited. The partition grid is 63 wells -
 * seven rows of nine - which is the size the art draws and the size AE2's own cell workbench uses.
 *
 * <p>The wells are AE2's {@link FakeSlot}, not a slot class of ours, and that is the whole design. A fake
 * slot exists for exactly this case: a grid whose entries are {@code AEKey}s rather than items, edited by
 * drag or by click, where nothing is handed over. It knows how to wrap a key into an item stack for
 * display, how to unwrap one on the way back, how to refuse entries the grid does not accept, and - the
 * part that is easy to get wrong - how to send the change to the server, which owns the real inventory.
 * Writing that again here would be a second implementation of a solved problem.
 *
 * <p>The partition itself lives on the cell item, so this menu is a view of it: the block entity mirrors
 * the cell's partition into the inventory the wells are backed by, and writes edits back.
 */
public class MenuEssentiaCellWorkbench extends AbstractContainerMenu {

    /** The cell being configured. */
    public static final int IDX_CELL = 36;

    /** The first partition well. */
    public static final int IDX_PARTITION_START = IDX_CELL + 1;

    private static final int PLAYER_SLOTS = 36;

    // From AE2's own cell workbench, which is the layout the art draws: the cell well at the top right,
    // the partition grid below it, and the player's inventory at the bottom.
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

    /** The cell's own slot container. On the client this is a scratch copy the server syncs. */
    private final Container cellContainer;

    /**
     * The partition, as the wells see it.
     *
     * <p>On the server this wraps the block entity's own inventory, so a well edit lands on the cell. On
     * the client the block entity is absent, so this is an empty stand-in the server's sync fills.
     */
    private final ConfigMenuInventory partition;

    /** Client constructor: the block entity lives on the client already, so nothing is wired here. */
    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, net.minecraft.network.RegistryFriendlyByteBuf buf) {
        // The cast picks the block-entity constructor: with two reference-typed third parameters, a bare
        // null would be ambiguous.
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

        // 2. The cell. Only a storage cell goes here - the whole block is about configuring one.
        addSlot(new Slot(cellContainer, 0, CELL_X, CELL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof ItemEssentiaCell;
            }
        });

        // 3. The partition wells.
        //
        // Positioned by hand, because a fake slot has nowhere to be told where it goes: AE2's FakeSlot
        // takes an inventory and an index and no coordinates at all, and its superclass leaves the slot at
        // 0,0. AE2 does not need to carry a position, because its own screens lay their slots out from the
        // style document afterwards - but this is a plain AbstractContainerScreen over this mod's own art,
        // with no style document and nothing to reposition anything. Left at 0,0 all 63 wells sit stacked
        // in the GUI's top-left corner, under each other.
        //
        // Writing x and y needs the access transformer in META-INF, which is the same access AE2 grants
        // itself for the same field.
        for (int index = 0; index < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; index++) {
            FakeSlot well = new FakeSlot(partition, index);
            well.x = PARTITION_X + (index % PARTITION_COLS) * PITCH;
            well.y = PARTITION_Y + (index / PARTITION_COLS) * PITCH;
            addSlot(well);
        }
    }

    /** How many partition wells there are. For JEI, which offers each as a drop target. */
    public static int partitionSlotCount() {
        return BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS;
    }

    /**
     * The menu index of a partition well.
     *
     * <p>Named here rather than counted at each call site - three places counting one layout is three
     * chances to count it differently, which is how the Knowledge Inscriber's grid ended up addressing the
     * wrong container.
     */
    public static int partitionSlotIndex(int well) {
        return IDX_PARTITION_START + well;
    }

    /** Whether a cell is in the slot. */
    public boolean hasCell() {
        if (workbench != null) {
            return workbench.hasCell();
        }
        return slotAt(IDX_CELL).getItem().getItem() instanceof ItemEssentiaCell;
    }

    /** The aspect recorded in a well, or {@code null}. */
    public @Nullable AEKey keyInWell(int well) {
        GenericStack stack = partition.getDelegate().getStack(well);
        return stack == null ? null : stack.what();
    }

    /** Replaces the partition with whatever the cell already holds. */
    public void partitionToContents() {
        if (workbench == null) {
            return;
        }
        ItemEssentiaCell.partitionToContents(workbench.getCell());
        workbench.setChanged();
        broadcastChanges();
    }

    /** Empties the partition. */
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
