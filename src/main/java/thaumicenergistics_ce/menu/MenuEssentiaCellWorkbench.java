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
 * 源质存储元件工作台的菜单：存储元件、它的升级槽，以及正在编辑的分区。
 * 它是一个 AE2 菜单，所以升级面板、元件槽和井都沿用 AE2 自己的
 * 处理。分区网格是 63 口井，一次标记以 [PartitionWellPayload] 到达；井里
 * 装什么属于 {@link CellPartitionEditor}，而这个菜单持有槽位。
 */
public class MenuEssentiaCellWorkbench extends UpgradeableMenu<BlockEntityEssentiaCellWorkbench>
        implements PartitionWellReceiver, IPartitionSlotHost {

    /** 客户端动作：用存储元件已有的内容填充这些井。 */
    private static final String ACTION_PARTITION = "partition";

    /** 客户端动作：清空每一口井。 */
    private static final String ACTION_CLEAR = "clear";

    // 存储元件位于右上角，美术图上它就在那里；井和物品栏由样式负责。
    private static final int CELL_X = 152;
    private static final int CELL_Y = 8;

    // 包级可见，供分区编辑器使用：它写入存储元件并通知宿主已变更。
    final BlockEntityEssentiaCellWorkbench workbench;

    // 由 [setupConfig] 构建，AE2 的基类在构造过程中会调用它，所以这个字段不能是 final。
    private CellPartitionEditor partitionEditor;

    // 在构造器体内构建：它测量的区间需要 AE2 已经归档好的槽位。
    private final CellWorkbenchShiftClick shiftClick;

    // 井会问这个菜单自己是否启用，所以保留该槽位；由 [setupInventorySlots] 设置它。
    private Slot cellSlot;

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, hostFrom(playerInventory, buf));
    }

    public MenuEssentiaCellWorkbench(
            int containerId, Inventory playerInventory, @Nullable BlockEntityEssentiaCellWorkbench workbench) {
        // AE2 的基类在自己的构造器里调用那三个 setup 方法，所以它们读取宿主。
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
                // 每个槽一个存储元件：元件的内容随身在它自己的物品堆里，所以堆叠会共用同一份内容。
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
            // AE2 自己的分区槽，所以背后没有元件的井会把自己画得暗淡而空。
            addSlot(new CellPartitionSlot(partitionEditor.partition(), this, well), SlotSemantics.CONFIG);
        }
    }

    /**
     * AE2 只在某个卡槽报告为启用时才显示升级面板 —— 边框、图标和 tooltip ——
     * 所以这些卡挂在存储元件上：槽位与 AE2 构建的相同，只有启用检查不同。
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
     * 菜单自己的槽里是否放着存储元件。井和卡槽都跟随这个槽，而不是
     * 方块实体：在客户端上宿主可能是个替身，而只有槽位是同步的。
     */
    boolean hasCellInMenu() {
        return cellSlot != null && cellSlot.getItem().getItem() instanceof ItemEssentiaCell;
    }

    /** 存储元件的槽，那一个元件就放在这里；shift 点击协作者会指明它的区间。 */
    Slot cellSlot() {
        return getSlots(SlotSemantics.STORAGE_CELL).get(0);
    }

    /**
     * AE2 问这个是为了画出一口井并让它可点：没有元件就没有东西可标记，于是这些
     * 井变得暗淡而空。
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

    /** 某个槽是哪口井；若该槽不属于分区的井，则为 {@code -1}。 */
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
     * 用存储元件已有的要素填充每一口井。由客户端发出：持有该元件的
     * 方块实体在服务端执行写入。
     */
    public void partitionToContents() {
        if (isClientSide()) {
            sendClientAction(ACTION_PARTITION);
            return;
        }
        partitionEditor.partitionToContents();
    }

    /** 清空每一口井。像 {@link #partitionToContents} 一样由客户端发出。 */
    public void clearPartition() {
        if (isClientSide()) {
            sendClientAction(ACTION_CLEAR);
            return;
        }
        partitionEditor.clearPartition();
    }

    /**
     * 应用从客户端到达的井编辑，这是通往服务端的唯一路径：一次标记，或用
     * {@code PartitionWellPayload.CLEAR} 去掉一个。没有元件则拒绝，因为分区由元件持有。
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
            // 没有东西可以接过这个物品堆，或者目标拒收：它就留在原处。
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
            // 服务于够不到方块实体的客户端的替身：没有什么可用来校验。
            return true;
        }
        var pos = workbench.getBlockPos();
        return level.getBlockEntity(pos) == workbench
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /**
     * 服务端打开的那个方块实体，客户端上则是打开数据包所指的那个；两者都
     * 没有时用替身，因为 AE2 的基类在构建槽位时会读宿主。
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
