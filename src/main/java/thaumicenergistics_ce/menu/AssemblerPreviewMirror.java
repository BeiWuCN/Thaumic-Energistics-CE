package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

/**
 * 菜单手里的样板井与合成预览：井从核心槽推导，预览读机器自己的槽。
 * 井每 tick 最多重推一次，签名的遍历要扫整个样板存储。
 */
final class AssemblerPreviewMirror {

    /**
     * 客户端上样板井的容器：客户端从核心槽推导它们，机器的容器没有更新标签可同步。
     */
    private final SimpleContainer display = new SimpleContainer(BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT);

    private final MenuArcaneAssembler menu;

    private int mirroredCore = -1;
    private int sampledCore = -1;
    private long coreSignatureTick = Long.MIN_VALUE;

    /**
     * 合成产物显示的那个镜像槽。拿槽对象不是索引，重排槽位也不会指错。
     */
    private @Nullable Slot targetSlot;

    private final Slot[] previewSlots = new Slot[BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT];

    AssemblerPreviewMirror(MenuArcaneAssembler menu) {
        this.menu = menu;
    }

    /** 背后没有机器时井所读的容器。 */
    SimpleContainer display() {
        return display;
    }

    /** 收下预览显示的槽位，在菜单把它们加完之后调。 */
    void watch(Slot target, Slot[] preview) {
        this.targetSlot = target;
        System.arraycopy(preview, 0, previewSlots, 0, preview.length);
    }

    void refresh() {
        ItemStack core = coreStack();
        // 每 tick 最多取一次：这是每帧调用，哈希要扫整个样板存储。
        long now = menu.playerInventory.player.level().getGameTime();
        if (now != coreSignatureTick) {
            coreSignatureTick = now;
            sampledCore = StackSignatures.of(core);
        }
        if (sampledCore == mirroredCore) {
            return;
        }
        mirroredCore = sampledCore;
        HandlerKnowledgeCore handler = coreHandler();
        List<ItemStack> outputs = handler == null ? List.of() : handler.storedOutputs();
        for (int i = 0; i < BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT; i++) {
            ItemStack stack = i < outputs.size() ? outputs.get(i) : ItemStack.EMPTY;
            if (menu.assembler != null) {
                writeMirrorSlot(BlockEntityArcaneAssembler.PATTERN_SLOT_START + i, stack);
            } else if (!ItemStack.matches(display.getItem(i), stack)) {
                display.setItem(i, stack.copy());
            }
        }
    }

    ItemStack getPreviewSlot(int index) {
        if (index < 0 || index >= previewSlots.length || previewSlots[index] == null) {
            return ItemStack.EMPTY;
        }
        return previewSlots[index].getItem();
    }

    ItemStack getPreviewResult() {
        return targetSlot == null ? ItemStack.EMPTY : targetSlot.getItem();
    }

    private void writeMirrorSlot(int machineSlot, ItemStack stack) {
        if (menu.assembler != null) {
            menu.assembler.getInventory().setItem(machineSlot, stack);
        }
    }

    private ItemStack coreStack() {
        int index = MenuArcaneAssembler.IDX_CORE;
        return index < menu.slots.size() ? menu.slots.get(index).getItem() : ItemStack.EMPTY;
    }

    private @Nullable HandlerKnowledgeCore coreHandler() {
        Level level = menu.assembler != null ? menu.assembler.getLevel() : menu.playerInventory.player.level();
        if (level == null) {
            return null;
        }
        return HandlerKnowledgeCore.of(coreStack(), level.registryAccess());
    }
}
