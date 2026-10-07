package thaumicenergistics_ce.blockentity;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.util.ConfigInventory;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.item.ItemEssentiaCell;

/**
 * 存储元件被告知可以存放哪些要素的地方。分区存在元件物品上，与
 * AE2 自己的元件一样，所以它能挺过一次驱动器或一个箱子；方块持有元件外加一份工作
 * 副本，因为回写会重建数据组件。同步标志守着加载与回写，
 * 因为一次写入会改动组件，而天真的重新加载会读错。
 */
public class BlockEntityEssentiaCellWorkbench extends ThEBaseBlockEntity implements IUpgradeableObject {

    public static final int CELL_SLOT = 0;

    /** 分区条目，与 AE2 自己的元件工作台以及屏幕上 7x9 的网格一致。 */
    public static final int PARTITION_SLOTS = 63;

    private final SimpleContainer inventory = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            if (syncing) {
                return;
            }
            BlockEntityEssentiaCellWorkbench.this.setChanged();
            BlockEntityEssentiaCellWorkbench.this.loadPartitionFromCell();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.getItem() instanceof ItemEssentiaCell;
        }
    };

    private final ConfigInventory partition = ConfigInventory.configTypes(PARTITION_SLOTS)
            .supportedTypes(Set.of(AEssentiaKeyType.INSTANCE))
            .changeListener(this::storePartitionInCell)
            .build();

    /** 元件自带的升级槽，每次调用重新读取，这样在菜单里换过的元件不会读到过期状态。 */
    private final IUpgradeInventory upgrades = new IUpgradeInventory() {

        @Override
        public int size() {
            // 即使没有元件也是三，因为客户端按这个数字构建自己的槽位。
            return ItemEssentiaCell.UPGRADE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IUpgradeInventory cell = upgradesOfCell();
            return slot < cell.size() ? cell.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            // 卡槽的物品堆上限取自物品栏，而接口自带的默认值是 99，
            // 所以没有这一条，一个槽位就会吞下整整一叠卡。一槽一卡，与 AE2 自己的相同。
            IUpgradeInventory cell = upgradesOfCell();
            return slot < cell.size() ? cell.getSlotLimit(slot) : 1;
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            IUpgradeInventory cell = upgradesOfCell();
            if (slot >= cell.size()) {
                return;
            }
            // 写到方块持有的元件物品上，随后与方块一起保存。
            cell.setItemDirect(slot, stack);
            BlockEntityEssentiaCellWorkbench.this.setChanged();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return upgradesOfCell().isItemValid(slot, stack);
        }

        @Override
        public ItemLike getUpgradableItem() {
            return hasCell() ? getCell().getItem() : Items.AIR;
        }

        @Override
        public int getInstalledUpgrades(ItemLike item) {
            return upgradesOfCell().getInstalledUpgrades(item);
        }

        @Override
        public int getMaxInstalled(ItemLike item) {
            return upgradesOfCell().getMaxInstalled(item);
        }

        @Override
        public void readFromNBT(CompoundTag tag, String key, HolderLookup.Provider registries) {
            // 没什么可读：卡就在元件物品自己的组件里，随它一起保存。
        }

        @Override
        public void writeToNBT(CompoundTag tag, String key, HolderLookup.Provider registries) {
            // 没什么可写，理由相同。
        }
    };

    private boolean syncing;

    public BlockEntityEssentiaCellWorkbench(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ESSENTIA_CELL_WORKBENCH.get(), pos, state);
    }

    public SimpleContainer getInventory() {
        return inventory;
    }

    public ConfigInventory getPartition() {
        return partition;
    }

    public ItemStack getCell() {
        return inventory.getItem(CELL_SLOT);
    }

    public boolean hasCell() {
        return getCell().getItem() instanceof ItemEssentiaCell;
    }

    private void loadPartitionFromCell() {
        syncing = true;
        try {
            for (int slot = 0; slot < PARTITION_SLOTS; slot++) {
                partition.setStack(slot, null);
            }
            if (!hasCell()) {
                return;
            }
            ConfigInventory cellPartition = ItemEssentiaCell.partitionOf(getCell());
            if (cellPartition == null) {
                return;
            }
            for (int slot = 0; slot < Math.min(PARTITION_SLOTS, cellPartition.size()); slot++) {
                AEKey key = cellPartition.getKey(slot);
                if (key != null) {
                    partition.setStack(slot, new GenericStack(key, 1));
                }
            }
        } finally {
            syncing = false;
        }
    }

    private void storePartitionInCell() {
        if (syncing || !hasCell()) {
            return;
        }
        ConfigInventory cellPartition = ItemEssentiaCell.partitionOf(getCell());
        if (cellPartition == null) {
            return;
        }
        syncing = true;
        try {
            for (int slot = 0; slot < cellPartition.size(); slot++) {
                AEKey key = slot < PARTITION_SLOTS ? partition.getKey(slot) : null;
                cellPartition.setStack(slot, key == null ? null : new GenericStack(key, 1));
            }
            setChanged();
        } finally {
            syncing = false;
        }
    }

    @Override
    public IUpgradeInventory getUpgrades() {
        return upgrades;
    }

    private IUpgradeInventory upgradesOfCell() {
        if (!hasCell() || !(getCell().getItem() instanceof ItemEssentiaCell cell)) {
            return UpgradeInventories.empty();
        }
        return cell.getUpgrades(getCell());
    }

    public boolean accepts(AEKey key) {
        return key != null && key.getType() == AEssentiaKeyType.INSTANCE;
    }

    @Override
    public AbstractContainerMenu createMenu(
            int containerId, Inventory playerInventory,
            Player player) {
        return MachineMenus.essentiaCellWorkbench(containerId, playerInventory, this);
    }


    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // 用 ContainerHelper，不用 createTag：createTag 不写索引，任何空隙都会让后面每个物品前移。
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // 旧世界在 "Inventory" 下存的是裸列表；只有一个槽意味着位置 0 或什么都没有。
            var list = tag.getList("Inventory", CompoundTag.TAG_COMPOUND);
            if (!list.isEmpty()) {
                inventory.setItem(CELL_SLOT, ItemStack.parseOptional(registries, list.getCompound(0)));
            }
        }
        // 放在物品栏之后，这样存档里的元件回来时屏幕上就带着它的分区。
        loadPartitionFromCell();
    }

    public void dropContents() {
        if (level == null) {
            return;
        }
        var stack = inventory.getItem(CELL_SLOT);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    stack);
            inventory.setItem(CELL_SLOT, ItemStack.EMPTY);
        }
    }
}
