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
 * 菜单所持有的样板凹槽与合成预览：凹槽由核心槽位推导而来，
 * 预览则读取机器自己的槽位。凹槽每个 tick 最多重新推导
 * 一次，因为签名要遍历整个样板存储。
 */
final class AssemblerPreviewMirror {

    /**
     * 客户端上样板凹槽的容器：客户端从核心槽位推导它们，而
     * 机器的容器没有可供同步的更新标签。
     */
    private final SimpleContainer display = new SimpleContainer(BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT);

    private final MenuArcaneAssembler menu;

    private int mirroredCore = -1;
    private int sampledCore = -1;
    private long coreSignatureTick = Long.MIN_VALUE;

    /**
     * 合成产物所显示的那个被镜像的槽位。以槽位对象持有而非索引，这样重排
     * 槽位不会让它指错对象。
     */
    private @Nullable Slot targetSlot;

    private final Slot[] previewSlots = new Slot[BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT];

    AssemblerPreviewMirror(MenuArcaneAssembler menu) {
        this.menu = menu;
    }

    /** 背后没有机器时凹槽所读取的容器。 */
    SimpleContainer display() {
        return display;
    }

    /** 接收预览所显示的槽位，在菜单添加完它们之后调用。 */
    void watch(Slot target, Slot[] preview) {
        this.targetSlot = target;
        System.arraycopy(preview, 0, previewSlots, 0, preview.length);
    }

    void refresh() {
        ItemStack core = coreStack();
        // 每个 tick 最多取一次：这是每帧都会调用的，而哈希要遍历整个样板存储。
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
