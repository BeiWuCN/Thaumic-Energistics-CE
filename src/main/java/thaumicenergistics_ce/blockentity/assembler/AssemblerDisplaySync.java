package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.ClientSyncSend;
import thaumicenergistics_ce.util.ThELog;

/** 组装机的显示与网络同步：渲染器和菜单读这里的字段，更新标签在这里写入和读回。
 * 四个覆写在方块实体上（{@code getUpdateTag} 等），都转到这个类。
 */
final class AssemblerDisplaySync {

    /** 显示推送节流，4 tick 一次；每 tick 一包不值得。 */
    private static final int UPDATE_INTERVAL = 4;

    /** 要与 Jade 载荷 {@code ArcaneAssemblerProvider.TAG_DISCOUNT} 拼写一致，配对写死。 */
    private static final String TAG_GEAR_DISCOUNT = "GearDiscount";
    /** 渲染器预览的产物键。发送时钟比其余键慢，键缺失表示未改变。 */
    private static final String TAG_PREVIEW = "Preview";

    private final BlockEntityArcaneAssembler owner;

    private long lastUpdate;

    /** 渲染器用的副本，真实产物在 {@link BlockEntityArcaneAssembler#TARGET_SLOT}。 */
    private ItemStack previewStack = ItemStack.EMPTY;

    AssemblerDisplaySync(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    ItemStack previewStack() {
        return previewStack;
    }

    // ------------------------------------------------------------------
    // 写入与读取
    // ------------------------------------------------------------------

    /** 把同步的状态写进 {@code tag}：合成、vis 池、装备折扣，以及预览产物（时钟更慢）。 */
    void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        owner.craft.writeSync(tag);
        owner.vis.writeNbt(tag);
        tag.putInt(TAG_GEAR_DISCOUNT, owner.upgrades().gearDiscount());
        if (!owner.craft.isCrafting() || owner.craft.craftTicks() == 0 || owner.craft.craftTicks() % 100 == 0) {
            tag.put(TAG_PREVIEW, owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).saveOptional(registries));
        }
    }

    /** 客户端应用更新标签。基类默认实现会调 {@code loadAdditional} 清掉合成状态，这里要覆写。 */
    void applySyncedState(CompoundTag tag, HolderLookup.Provider registries) {
        owner.suppressNotify = true;
        try {
            owner.craft.readSync(tag);
            owner.vis.readSync(tag);
            owner.upgrades().setGearDiscount(tag.getInt(TAG_GEAR_DISCOUNT));
            // 缺少该键表示未改变；产物走更慢的时钟。
            if (tag.contains(TAG_PREVIEW)) {
                previewStack = ItemStack.parseOptional(registries, tag.getCompound(TAG_PREVIEW));
            }
        } finally {
            owner.suppressNotify = false;
        }
    }

    // ------------------------------------------------------------------
    // 显示
    // ------------------------------------------------------------------

    void clearDisplay(boolean report) {
        boolean hadAnything =
                !owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).isEmpty();
        owner.suppressNotify = true;
        try {
            owner.inventory.setItem(BlockEntityArcaneAssembler.TARGET_SLOT, ItemStack.EMPTY);
            for (int i = 0; i < BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT; i++) {
                if (!owner.inventory.getItem(BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i).isEmpty()) {
                    hadAnything = true;
                }
                owner.inventory.setItem(BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i, ItemStack.EMPTY);
            }
        } finally {
            owner.suppressNotify = false;
        }
        if (report && hadAnything) {
            ThELog.LOG.info(
                    "[assembler] at {} cleared a leftover craft display: nothing is crafting", owner.getBlockPos());
        }
    }

    // ------------------------------------------------------------------
    // 显示区段
    // ------------------------------------------------------------------

    /** 机器自己写的槽位：样板镜像、目标槽、预览网格。
     * 玩家手里拿的是副本，这几个槽里的东西不掉落。 */
    static boolean isMachineOwned(int slot) {
        return slot >= BlockEntityArcaneAssembler.PATTERN_SLOT_START
                        && slot < BlockEntityArcaneAssembler.GEAR_SLOT_START
                || slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                        && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START;
    }

    /** 目标槽和预览网格由运行中的合成覆盖，玩家不能放物品。 */
    static boolean isDisplaySlot(int slot) {
        return slot == BlockEntityArcaneAssembler.TARGET_SLOT
                || slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                        && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START;
    }

    /** 写入运行中合成的显示：产物进目标槽，3x3 进预览网格。
     * 要放在 notify 守卫之后，否则容器监听器把每次写入当玩家改动，重从核心重建样板列表。 */
    void refreshDisplaySlots(ItemStack target, List<ItemStack> grid) {
        owner.suppressNotify = true;
        try {
            owner.inventory.setItem(BlockEntityArcaneAssembler.TARGET_SLOT, target);
            for (int i = 0; i < BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT; i++) {
                ItemStack cell = i < grid.size() ? grid.get(i) : ItemStack.EMPTY;
                owner.inventory.setItem(
                        BlockEntityArcaneAssembler.PREVIEW_SLOT_START + i,
                        cell.isEmpty() ? ItemStack.EMPTY : cell.copy());
            }
        } finally {
            owner.suppressNotify = false;
        }
    }

    /** 样板槽位是公布集合的镜像，玩家从菜单读到的是它。 */
    void refreshPatternSlots() {
        if (owner.getLevel() == null) {
            return;
        }
        owner.patternCache.refresh();
        owner.suppressNotify = true;
        try {
            for (int i = 0; i < BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT; i++) {
                ItemStack stack = ItemStack.EMPTY;
                if (i < owner.patternCache.patterns().size()) {
                    List<GenericStack> outputs = owner.patternCache.patterns().get(i).getOutputs();
                    if (!outputs.isEmpty() && outputs.getFirst().what() instanceof AEItemKey key) {
                        stack = key.getReadOnlyStack();
                    }
                }
                owner.inventory.setItem(BlockEntityArcaneAssembler.PATTERN_SLOT_START + i, stack);
            }
        } finally {
            owner.suppressNotify = false;
        }
    }

    /** 推送显示给观看的玩家，每 {@link #UPDATE_INTERVAL} tick 最多一次。 */
    void markDisplayForUpdate() {
        if (owner.getLevel() == null) {
            return;
        }
        long now = owner.getLevel().getGameTime();
        if (now - lastUpdate < UPDATE_INTERVAL) {
            return;
        }
        lastUpdate = now;
        markForUpdate();
    }

    void markForUpdate() {
        if (owner.getLevel() == null) {
            return;
        }
        owner.setChanged();
        ClientSyncSend.sendBlockEntityUpdate(owner);
    }
}