package thaumicenergistics_ce.menu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.CellPartitionSlot;
import appeng.menu.slot.IPartitionSlotHost;
import appeng.menu.slot.RestrictedInputSlot;
import appeng.util.ConfigMenuInventory;
import com.leclowndu93150.thaumaturge.api.aspect.Aspects;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.BlockEntityEssentiaCellWorkbench;
import thaumicenergistics_ce.init.ModBlocks;
import thaumicenergistics_ce.init.ModMenuTypes;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.item.ItemEssentiaCell;
import thaumicenergistics_ce.network.PartitionWellPayload;
import thaumicenergistics_ce.network.PartitionWellReceiver;
import thaumicenergistics_ce.util.ThELog;

/**
 * The Essentia Cell Workbench's menu: the cell, its upgrade slots, and the partition being edited.
 * <ul>
 *   <li>An AE2 menu, so the upgrades panel, the cell slot and the wells come with AE2's own handling.
 *   <li>The partition grid is 63 wells; a mark arrives as {@code PartitionWellPayload}.
 *   <li>The partition lives on the cell item; the block entity mirrors it into the wells and writes back.
 * </ul>
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

    private final BlockEntityEssentiaCellWorkbench workbench;

    private ConfigMenuInventory partition;

    // The player side, first slot and one past its last: a shift-click moves into that range.
    private final int playerSlotStart;

    private final int playerSlotEnd;

    // The card slots, same convention: a card shift-clicked in the inventory goes into that range.
    private final int cardSlotStart;

    private final int cardSlotEnd;

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
        // AE2 files the hotbar under its own semantic and adds it before the main inventory, so the first
        // PLAYER_INVENTORY slot sits nine slots in, and reading both groups keeps a shift-click bounded.
        List<Slot> playerSide = new ArrayList<>(getSlots(SlotSemantics.PLAYER_HOTBAR));
        playerSide.addAll(getSlots(SlotSemantics.PLAYER_INVENTORY));
        int[] playerRange = slotRange(playerSide);
        this.playerSlotStart = playerRange[0];
        this.playerSlotEnd = playerRange[1];
        int[] cardRange = slotRange(getSlots(SlotSemantics.UPGRADE));
        this.cardSlotStart = cardRange[0];
        this.cardSlotEnd = cardRange[1];
        registerClientAction(ACTION_PARTITION, this::partitionToContents);
        registerClientAction(ACTION_CLEAR, this::clearPartition);
    }

    /**
     * A group of slots as the one range {@code moveItemStackTo} wants: lowest index and one past the
     * highest; an empty group becomes an empty range at the end, so a move into it just fails.
     */
    private int[] slotRange(List<Slot> group) {
        int start = Integer.MAX_VALUE;
        int end = 0;
        for (Slot slot : group) {
            start = Math.min(start, slot.index);
            end = Math.max(end, slot.index + 1);
        }
        return end == 0 ? new int[] {slots.size(), slots.size()} : new int[] {start, end};
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
        this.partition = getHost().getPartition().createMenuWrapper();
        for (int well = 0; well < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; well++) {
            // AE2's own partition slot, so a well with no cell behind it draws itself faint and empty.
            addSlot(new CellPartitionSlot(partition, this, well), SlotSemantics.CONFIG);
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
    private boolean hasCellInMenu() {
        return cellSlot != null && cellSlot.getItem().getItem() instanceof ItemEssentiaCell;
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
        GenericStack stack = partition.getDelegate().getStack(well);
        return stack == null ? null : stack.what();
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
        if (!hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so the wells cannot be filled");
            return;
        }
        ItemEssentiaCell.partitionToContents(workbench.getCell());
        workbench.setChanged();
        broadcastChanges();
    }

    /** Empties every well. Sent from the client, like {@link #partitionToContents}. */
    public void clearPartition() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }
        if (!hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so there is no partition to clear");
            return;
        }
        ItemEssentiaCell.clearPartition(workbench.getCell());
        workbench.setChanged();
        broadcastChanges();
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
        if (workbench == null) {
            return;
        }
        if (well < 0 || well >= BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS) {
            ThELog.LOG.warn("[cell-partition] well {} is out of range", well);
            return;
        }
        if (!hasCell()) {
            ThELog.LOG.warn("[cell-partition] no cell in the workbench, so well {} has nowhere to go", well);
            return;
        }
        if (PartitionWellPayload.CLEAR.equals(aspectId)) {
            // A mark is taken out by clicking its well, where AE2 would pick the entry back up.
            clearWell(well);
            return;
        }

        Holder<IAspect> aspect =
                Aspects.resolve(
                        player.level(),
                        ResourceKey.create(
                                IAspect.REGISTRY_KEY, aspectId));
        if (aspect == null) {
            // An id the server does not know: dropping it beats a partition entry that can never match.
            ThELog.LOG.warn("[cell-partition] the server cannot resolve aspect {}", aspectId);
            return;
        }
        AEssentiaKey key = AEssentiaKey.of(aspect);
        if (key == null) {
            // Not registry-backed: no id, so the entry could never match anything.
            ThELog.LOG.warn("[cell-partition] aspect {} is not a registry entry", aspectId);
            return;
        }

        // One type, one well: a key already marked elsewhere moves here instead of appearing twice.
        for (int other = 0; other < BlockEntityEssentiaCellWorkbench.PARTITION_SLOTS; other++) {
            if (other != well && key.equals(keyInWell(other))) {
                clearWell(other);
            }
        }

        // One, because a partition entry is a type rather than an amount - how much the cell holds is
        // decided by its size. Writing here is what fires the block entity's listener, which stores it.
        partition.getDelegate().setStack(well, new GenericStack(key, 1));
        workbench.setChanged();
        broadcastChanges();
        // Read straight back: "wrote" and "now holds" as two separate facts, for the failure being chased.
        ThELog.LOG.info(
                "[cell-partition] wrote {} to well {}; it now holds {}",
                key, well, keyInWell(well));
    }

    private void clearWell(int well) {
        partition.getDelegate().setStack(well, null);
        workbench.setChanged();
        broadcastChanges();
        ThELog.LOG.info("[cell-partition] took the mark out of well {}; it now holds {}", well, keyInWell(well));
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
        Slot cellSlot = getSlots(SlotSemantics.STORAGE_CELL).get(0);

        if (slot == cellSlot) {
            if (!moveItemStackTo(stack, playerSlotStart, playerSlotEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index >= playerSlotStart) {
            if (stack.getItem() instanceof ItemEssentiaCell && !cellSlot.hasItem()) {
                // The destination is the cell slot, not the clicked one: the clicked slot's own range
                // merged the stack into itself, so the range names where the stack is going.
                if (!moveItemStackTo(stack, cellSlot.index, cellSlot.index + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (hasCellInMenu() && Upgrades.isUpgradeCardItem(stack)) {
                // A card rides on the cell, so there is nowhere to put one without it. Which cards the cell
                // takes is the cell's own upgrade inventory's call, asked through the slots' mayPlace.
                if (!moveItemStackTo(stack, cardSlotStart, cardSlotEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        } else if (getSlots(SlotSemantics.UPGRADE).contains(slot)) {
            if (!moveItemStackTo(stack, playerSlotStart, playerSlotEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // A well: a mark is a type, not a pile, so there is nothing for shift-click to move.
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
