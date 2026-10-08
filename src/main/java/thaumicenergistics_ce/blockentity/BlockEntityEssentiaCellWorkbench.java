package thaumicenergistics_ce.blockentity;

import java.util.List;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
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
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import thaumicenergistics_ce.block.ThEBaseBlockEntity;
import thaumicenergistics_ce.init.MachineMenus;
import thaumicenergistics_ce.init.ModBlockEntities;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.item.ItemEssentiaCell;
import thaumicenergistics_ce.util.ThEItemTags;

/**
 * 告诉存储元件它能存哪些要素处。分区存在元件物品上，跟 AE2 自己的元件一样，
 * 故过一次驱动器或箱子还在；方块持有元件外加一份工作副本，
 * 因回写会重建数据组件。同步标志守住加载和回写：一次写入会改组件，天真地重载会读错。
 */
public class BlockEntityEssentiaCellWorkbench extends ThEBaseBlockEntity implements IUpgradeableObject {

    public static final int CELL_SLOT = 0;

    /** 分区条目数，与 AE2 自己的元件工作台和界面素材里的 7x9 网格一致。 */
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

    /** 元件自带的升级槽，每次调用重读，菜单里换过元件也不会读到旧状态。 */
    private final IUpgradeInventory upgrades = new IUpgradeInventory() {

        @Override
        public int size() {
            // 没有元件也是三：客户端按这个数构建自己的槽位。
            return ItemEssentiaCell.UPGRADE_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            IUpgradeInventory cell = upgradesOfCell();
            return slot < cell.size() ? cell.getStackInSlot(slot) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            // 卡槽的物品堆上限取自物品栏，而接口默认是 99，
            // 少了这一条一个槽会吞下整叠卡。一槽一卡，同 AE2 自己。
            IUpgradeInventory cell = upgradesOfCell();
            return slot < cell.size() ? cell.getSlotLimit(slot) : 1;
        }

        @Override
        public void setItemDirect(int slot, ItemStack stack) {
            IUpgradeInventory cell = upgradesOfCell();
            if (slot >= cell.size()) {
                return;
            }
            // 写到方块持有的元件物品上，随后与方块一起存。
            cell.setItemDirect(slot, stack);
            BlockEntityEssentiaCellWorkbench.this.setChanged();
        }

        @Override
        public ResourceHandler<ItemResource> toResourceHandler() {
            return upgradesOfCell().toResourceHandler();
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
        public void readFromNBT(ValueInput input, String key) {
            // 没东西可读：卡就在元件物品自己的组件里，随它一起存。
        }

        @Override
        public void writeToNBT(ValueOutput output, String key) {
            // 没东西可读：卡就在元件物品自己的组件里，随它一起存。
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
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        // 用 ContainerHelper，不用 createTag：createTag 不写索引，任何空隙都会让每件物品挪位。
        ContainerHelper.saveAllItems(output, inventory.getItems());
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        if (input.childrenList(ContainerHelper.TAG_ITEMS).isPresent()) {
            ContainerHelper.loadAllItems(input, inventory.getItems());
        } else {
            // 旧世界在 "Inventory" 下留的是裸列表；只有一个槽位，就意味着位置 0 或什么都没有。
            var list = input.read("Inventory", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
            if (!list.isEmpty()) {
                inventory.setItem(CELL_SLOT, list.get(0));
            }
        }
        // 放在物品栏之后：存档里的元件回来时屏幕上就带着它的分区。
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
    @Override
    public void preRemoveSideEffects(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        dropContents();
        super.preRemoveSideEffects(pos, state);
    }
}
