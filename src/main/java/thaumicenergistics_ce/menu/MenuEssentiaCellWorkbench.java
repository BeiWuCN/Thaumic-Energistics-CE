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
 * 源质存储元件工作台的菜单：存储元件槽、升级槽和正在编辑的分区。
 * 分区网格 63 口井，一次标记以 [PartitionWellPayload] 到达。
 * 井里装什么归 {@link CellPartitionEditor}，菜单只持槽位；升级面板和元件槽沿用 AE2 基类。
 */
public class MenuEssentiaCellWorkbench extends UpgradeableMenu<BlockEntityEssentiaCellWorkbench>
        implements PartitionWellReceiver, IPartitionSlotHost {

    /** 客户端动作：按存储元件现有内容填满井。 */
    private static final String ACTION_PARTITION = "partition";

    /** 客户端动作：清空所有井。 */
    private static final String ACTION_CLEAR = "clear";

    // 存储元件槽的 x，出自美术图。
    private static final int CELL_X = 152;
    private static final int CELL_Y = 8;

    // 包级可见：分区编辑器要写存储元件并通知宿主。
    final BlockEntityEssentiaCellWorkbench workbench;

    // 不能是 final：AE2 基类在构造器里就调用 [setupConfig]，字段由它赋值。
    private CellPartitionEditor partitionEditor;

    // 在构造器体内建：它量的区间要 AE2 先归档好槽位。
    private final CellWorkbenchShiftClick shiftClick;

    // 井会问这个菜单是否启用，槽位得留着；[setupInventorySlots] 赋值。
    private Slot cellSlot;

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, hostFrom(playerInventory, buf));
    }

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, @Nullable BlockEntityEssentiaCellWorkbench workbench) {
        // AE2 基类在自己的构造器里调用三个 setup 方法，它们只能读传入的宿主。
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
                // 每个槽只放一个元件：内容存在物品堆自己身上，堆叠会共用同一份内容。
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
            // AE2 自带的分区槽：背后没有元件的井会画成暗淡空槽。
            addSlot(new CellPartitionSlot(partitionEditor.partition(), this, well), SlotSemantics.CONFIG);
        }
    }

    /**
     * AE2 只在卡槽报启用时才画升级面板（边框、图标、tooltip）。
     * 这里的卡挂在存储元件上：槽位与 AE2 建的一样，只改启用检查。
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
     * 菜单的槽里有没有元件。井和卡槽跟着这个槽走，不看方块实体：
     * 客户端上的宿主可能是替身，只有槽位是同步的。
     */
    boolean hasCellInMenu() {
        return cellSlot != null && cellSlot.getItem().getItem() instanceof ItemEssentiaCell;
    }

    /** 元件槽，元件就放这里；shift 点击协作者靠它定位区间。 */
    Slot cellSlot() {
        return getSlots(SlotSemantics.STORAGE_CELL).get(0);
    }

    /**
     * AE2 问这个来画井、让它可点：没有元件就没东西可标记，井变暗淡空槽。
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

    /** 某个槽是第几口井；不属于分区的井返回 {@code -1}。 */
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
     * 按元件现有的要素填满每口井。客户端发出，写入在服务端的方块实体里。
     */
    public void partitionToContents() {
        if (isClientSide()) {
            sendClientAction(ACTION_PARTITION);
            return;
        }
        partitionEditor.partitionToContents();
    }

    /** 清空每口井。和 {@link #partitionToContents} 一样由客户端发出。 */
    public void clearPartition() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }
        partitionEditor.clearPartition();
    }

    /**
     * 应用客户端发来的井编辑，这是通往服务端的唯一路径：标记一口井，或用
     * {@code PartitionWellPayload.CLEAR} 清掉。没有元件就拒绝，分区归元件所有。
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
            // 没东西接手或目标拒收，物品堆留在原处。
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
            // 客户端替身够不到方块实体，这里没有可校验的东西。
            return true;
        }
        var pos = workbench.getBlockPos();
        return level.getBlockEntity(pos) == workbench
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /**
     * 服务端打开的那个方块实体，客户端上是打开数据包指的那个；都没有就用替身，
     * AE2 基类建槽位时会读宿主。
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
