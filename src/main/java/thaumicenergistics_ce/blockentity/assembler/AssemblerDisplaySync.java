package thaumicenergistics_ce.blockentity.assembler;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.ClientSyncSend;
import thaumicenergistics_ce.util.ThELog;

/** 组装机的显示及其网络同步，从 {@link BlockEntityArcaneAssembler} 拆出：
 * 渲染器和菜单绘制什么、多久发出一次，以及更新标签如何发出与回来。
 * 同包，所以可直接访问机器状态；四个覆写（{@code getUpdateTag}、
 * {@code handleUpdateTag}、{@code onDataPacket}）留在方块实体上并委托到这里。
 */
final class AssemblerDisplaySync {

    /** 对每 tick 显示推送的节流；按每 tick 一个数据包推送合成进度不
     * 值得，而在这一速率下机器看起来仍是实时的。 */
    private static final int UPDATE_INTERVAL = 4;

    /** 线格式名称。第一个与 Jade 载荷（{@code ArcaneAssemblerProvider.TAG_DISCOUNT}）
     * 拼写相同；两者是一对，必须保持相等。 */
    private static final String TAG_GEAR_DISCOUNT = "GearDiscount";
    /** 渲染器预览的产物：以更慢的时钟发送，所以缺少该键表示"未改变"。 */
    private static final String TAG_PREVIEW = "Preview";

    private final BlockEntityArcaneAssembler owner;

    private long lastUpdate;

    /** 运行中合成产物的仅渲染器副本，从更新标签写入：真实的那份
     * 在 {@link BlockEntityArcaneAssembler#TARGET_SLOT} 中。 */
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

    /** 把机器状态的同步部分加入 {@code tag}：合成、vis 池、装备
     * 折扣，以及——比其余部分更慢的时钟——渲染器预览的产物。 */
    void writeSync(CompoundTag tag, HolderLookup.Provider registries) {
        owner.craft.writeSync(tag);
        owner.vis.writeNbt(tag);
        tag.putInt(TAG_GEAR_DISCOUNT, owner.upgrades().gearDiscount());
        if (!owner.craft.isCrafting() || owner.craft.craftTicks() == 0 || owner.craft.craftTicks() % 100 == 0) {
            tag.put(TAG_PREVIEW, owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT).saveOptional(registries));
        }
    }

    /** 在客户端应用更新标签，即每 tick 更新走的路径。数据包落在这里，其
     * 默认实现最终调用 {@code loadAdditional}，会清空合成状态。 */
    void applySyncedState(CompoundTag tag, HolderLookup.Provider registries) {
        owner.suppressNotify = true;
        try {
            owner.craft.readSync(tag);
            owner.vis.readSync(tag);
            owner.upgrades().setGearDiscount(tag.getInt(TAG_GEAR_DISCOUNT));
            // 显示：缺少该键表示"未改变"，产物以更慢的时钟发出。
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

    /** 清空目标槽位和预览槽位——渲染器和菜单绘制的内容——仅在
     * 确实有东西可清空时才报告。 */
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

    /** 机器为自己写入的区段：样板镜像、目标槽和预览
     * 网格。它们原本都不是玩家的物品——玩家持有的是副本——所以都不掉落。 */
    static boolean isMachineOwned(int slot) {
        return slot >= BlockEntityArcaneAssembler.PATTERN_SLOT_START
                        && slot < BlockEntityArcaneAssembler.GEAR_SLOT_START
                || slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                        && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START;
    }

    /** 该显示中玩家永远不能放入物品的部分：目标槽和预览
     * 网格，二者都会被运行中的合成覆盖。 */
    static boolean isDisplaySlot(int slot) {
        return slot == BlockEntityArcaneAssembler.TARGET_SLOT
                || slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                        && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START;
    }

    /** 写入运行中合成的显示——产物放入目标槽，3x3 放入预览网格——
     * 位于 notify 守卫之后：没有它，容器的监听器会把每次写入当成玩家
     * 在改动机器，并每次都从核心重建样板列表。网格在服务端这里存在，因为
     * 运行中的合成存在；客户端收到的是副本。 */
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

    /** 从公布的集合重写样板槽位：玩家读到的镜像，不是真实
     * 物品栏。 */
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

    /** 把显示推送给观看的玩家，最多每 {@link #UPDATE_INTERVAL} tick 一次。 */
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