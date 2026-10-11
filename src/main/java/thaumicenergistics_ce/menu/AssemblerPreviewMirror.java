package thaumicenergistics_ce.menu;

import java.util.List;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.blockentity.assembler.BlockEntityArcaneAssembler;
import thaumicenergistics_ce.inventory.HandlerKnowledgeCore;

final class AssemblerPreviewMirror {

    private final SimpleContainer display = new SimpleContainer(BlockEntityArcaneAssembler.PATTERN_SLOT_COUNT);

    private final MenuArcaneAssembler menu;

    private int mirroredCore = -1;
    private int sampledCore = -1;
    private long coreSignatureTick = Long.MIN_VALUE;

    private @Nullable Slot targetSlot;

    private final Slot[] previewSlots = new Slot[BlockEntityArcaneAssembler.PREVIEW_SLOT_COUNT];

    AssemblerPreviewMirror(MenuArcaneAssembler menu) {
        this.menu = menu;
    }

    SimpleContainer display() {
        return display;
    }

    void watch(Slot target, Slot[] preview) {
        this.targetSlot = target;
        System.arraycopy(preview, 0, previewSlots, 0, preview.length);
    }

    void refresh() {
        ItemStack core = coreStack();
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
