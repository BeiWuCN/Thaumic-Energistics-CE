package thaumicenergistics_ce.blockentity.inscriber;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumicenergistics_ce.init.ModItems;

/**
 * 铭刻机的槽位：核心、镜像、玩家在菜单里拼出的网格。
 * 网格一次写完算一次改动；写元件期间通知被压住，否则会解析出半旧的网格。
 * 保存保留槽位索引，带空隙的网格回来时仍带空隙。
 */
final class InscriberInventory {

    private final BlockEntityKnowledgeInscriber inscriber;

    private final SimpleContainer inventory =
            new SimpleContainer(BlockEntityKnowledgeInscriber.SLOT_COUNT) {
                @Override
                public void setChanged() {
                    super.setChanged();
                    if (absorbing) {
                        return;
                    }
                    inscriber.setChanged();
                    inscriber.contentsChanged();
                }

                @Override
                public boolean canPlaceItem(int slot, ItemStack stack) {
                    // 只有核心槽真正持有物品，旁边的槽只镜像核心已经存着的东西。
                    // 那些镜像槽不许写入。
                    return slot == BlockEntityKnowledgeInscriber.CORE_SLOT
                            && stack.is(ModItems.KNOWLEDGE_CORE.get());
                }
            };

    /** 网格正在被逐格写入期间为 true。
     * 逐格通知会解析出一个换了一半的网格，玩家会看着旧配方的物品一件件被挤出去。 */
    private boolean absorbing;

    InscriberInventory(BlockEntityKnowledgeInscriber inscriber) {
        this.inscriber = inscriber;
    }

    SimpleContainer inventory() {
        return inventory;
    }

    List<ItemStack> cells() {
        List<ItemStack> cells = new ArrayList<>(BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT);
        for (int i = 0; i < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; i++) {
            cells.add(inventory.getItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + i));
        }
        return cells;
    }

    void setCell(int cell, ItemStack stack) {
        Level level = inscriber.getLevel();
        if (level == null || level.isClientSide
                || cell < 0 || cell >= BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT) {
            return;
        }
        int slot = BlockEntityKnowledgeInscriber.GRID_SLOT_START + cell;
        ItemStack current = inventory.getItem(slot);
        ItemStack wanted = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        if (ItemStack.matches(current, wanted)) {
            return;
        }
        inventory.setItem(slot, wanted);
    }

    void setAll(List<ItemStack> cells) {
        Level level = inscriber.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasAbsorbing = absorbing;
        absorbing = true;
        try {
            for (int i = 0; i < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; i++) {
                ItemStack wanted = i < cells.size() ? cells.get(i) : ItemStack.EMPTY;
                inventory.setItem(BlockEntityKnowledgeInscriber.GRID_SLOT_START + i,
                        wanted.isEmpty() ? ItemStack.EMPTY : wanted.copyWithCount(1));
            }
        } finally {
            absorbing = wasAbsorbing;
        }
        inscriber.contentsChanged();
    }

    void clear() {
        boolean wasAbsorbing = absorbing;
        absorbing = true;
        try {
            for (int i = 0; i < BlockEntityKnowledgeInscriber.GRID_SLOT_COUNT; i++) {
                int slot = BlockEntityKnowledgeInscriber.GRID_SLOT_START + i;
                if (!inventory.getItem(slot).isEmpty()) {
                    inventory.setItem(slot, ItemStack.EMPTY);
                }
            }
        } finally {
            absorbing = wasAbsorbing;
        }
    }

    boolean hasCore() {
        return inventory.getItem(BlockEntityKnowledgeInscriber.CORE_SLOT).is(ModItems.KNOWLEDGE_CORE.get());
    }

    ItemStack coreStack() {
        return inventory.getItem(BlockEntityKnowledgeInscriber.CORE_SLOT);
    }

    void saveItems(CompoundTag tag, HolderLookup.Provider registries) {
        // 不用 SimpleContainer.createTag：它只写非空槽位、不记录索引。
        // 那样网格回来时空隙没了，每个物品都往前挪。
        ContainerHelper.saveAllItems(tag, inventory.getItems(), registries);
    }

    void loadItems(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(ContainerHelper.TAG_ITEMS, Tag.TAG_LIST)) {
            ContainerHelper.loadAllItems(tag, inventory.getItems(), registries);
        } else {
            // 本次改动前存档的世界在 "Inventory" 下存的是裸列表，本来就没有空隙。
            // 按位置读取，下一次保存就会写出新格式。
            loadLegacy(tag.getList("Inventory", Tag.TAG_COMPOUND), registries);
        }
    }

    /** 按位置读旧的裸列表形式。
     * 那种形式丢掉了条目原本来自哪个槽位，列表里的槽位数还可能多于本版本拥有的。 */
    private void loadLegacy(ListTag list, HolderLookup.Provider registries) {
        int kept = Math.min(list.size(), BlockEntityKnowledgeInscriber.SLOT_COUNT);
        for (int i = 0; i < kept; i++) {
            inventory.setItem(i, ItemStack.parseOptional(registries, list.getCompound(i)));
        }
    }

    /** 方块被破坏时掉落核心与镜像；网格是草稿纸，不掉。 */
    void dropItems() {
        Level level = inscriber.getLevel();
        if (level == null) {
            return;
        }
        BlockPos pos = inscriber.getBlockPos();
        for (int i = 0; i < BlockEntityKnowledgeInscriber.SLOT_COUNT; i++) {
            // 网格里是玩家本来就有的物品，掉落等于把 JEI 拖进来的东西复制一份。
            if (i >= BlockEntityKnowledgeInscriber.GRID_SLOT_START) {
                continue;
            }
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
