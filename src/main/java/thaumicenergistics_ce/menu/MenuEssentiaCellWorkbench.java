package thaumicenergistics_ce.menu;

import appeng.api.stacks.AEKey;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.menu.SlotSemantics;
import appeng.menu.guisync.ClientActionKey;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.CellPartitionSlot;
import appeng.menu.slot.IPartitionSlotHost;
import appeng.menu.slot.RestrictedInputSlot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.registry.TECEBlocks;
import thaumicenergistics_ce.registry.TECEMenuTypes;
import thaumicenergistics_ce.item.ItemEssentiaCell;
import thaumicenergistics_ce.network.PartitionWellReceiver;

public class MenuEssentiaCellWorkbench extends UpgradeableMenu<BlockEntityEssentiaCellWorkbench>
        implements PartitionWellReceiver, IPartitionSlotHost {

    private static final ClientActionKey<Void> ACTION_PARTITION = new ClientActionKey<>("partition");

    private static final ClientActionKey<Void> ACTION_CLEAR = new ClientActionKey<>("clear");

    private static final int CELL_X = 152;
    private static final int CELL_Y = 8;

    final BlockEntityEssentiaCellWorkbench workbench;

    private CellPartitionEditor partitionEditor;

    private final CellWorkbenchShiftClick shiftClick;

    private Slot cellSlot;

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, hostFrom(playerInventory, buf));
    }

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, @Nullable BlockEntityEssentiaCellWorkbench workbench) {
        super(TECEMenuTypes.ESSENTIA_CELL_WORKBENCH.get(), containerId, playerInventory, host(workbench));
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
            addSlot(new CellPartitionSlot(partitionEditor.partition(), this, well), SlotSemantics.CONFIG);
        }
    }

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

    boolean hasCellInMenu() {
        return cellSlot != null && cellSlot.getItem().getItem() instanceof ItemEssentiaCell;
    }

    Slot cellSlot() {
        return getSlots(SlotSemantics.STORAGE_CELL).get(0);
    }

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

    public int wellOf(Slot slot) {
        return getSlots(SlotSemantics.CONFIG).indexOf(slot);
    }

    public boolean hasCell() {
        return workbench.hasCell();
    }

    public @Nullable AEKey keyInWell(int well) {
        return partitionEditor.keyInWell(well);
    }

    public void partitionToContents() {
        if (isClientSide()) {
            sendClientAction(ACTION_PARTITION);
            return;
        }
        partitionEditor.partitionToContents();
    }

    public void clearPartition() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }
        partitionEditor.clearPartition();
    }

    @Override
    public void setPartitionWell(
            int well,
            Identifier aspectId,
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
            return true;
        }
        var pos = workbench.getBlockPos();
        return level.getBlockEntity(pos) == workbench
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

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
                pos, TECEBlocks.ESSENTIA_CELL_WORKBENCH.get().defaultBlockState());
    }
}
