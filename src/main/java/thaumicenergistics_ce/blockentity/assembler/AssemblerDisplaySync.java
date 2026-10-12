package thaumicenergistics_ce.blockentity.assembler;

import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.blockentity.ClientSyncSend;

final class AssemblerDisplaySync {

    private static final int UPDATE_INTERVAL = 4;

    private static final String TAG_GEAR_DISCOUNT = "GearDiscount";
    private static final String TAG_PREVIEW = "Preview";

    private final BlockEntityArcaneAssembler owner;

    private long lastUpdate;

    private ItemStack previewStack = ItemStack.EMPTY;

    AssemblerDisplaySync(BlockEntityArcaneAssembler owner) {
        this.owner = owner;
    }

    ItemStack previewStack() {
        return previewStack;
    }


    void writeSync(ValueOutput output) {
        owner.craft.writeSync(output);
        owner.vis.writeNbt(output);
        output.putInt(TAG_GEAR_DISCOUNT, owner.upgrades().gearDiscount());
        if (!owner.craft.isCrafting() || owner.craft.craftTicks() == 0 || owner.craft.craftTicks() % 100 == 0) {
            output.store(TAG_PREVIEW, ItemStack.OPTIONAL_CODEC,
                    owner.inventory.getItem(BlockEntityArcaneAssembler.TARGET_SLOT));
        }
    }

    void applySyncedState(ValueInput input) {
        owner.suppressNotify = true;
        try {
            owner.craft.readSync(input);
            owner.vis.readSync(input);
            owner.upgrades().setGearDiscount(input.getIntOr(TAG_GEAR_DISCOUNT, 0));
            input.read(TAG_PREVIEW, ItemStack.OPTIONAL_CODEC).ifPresent(stack -> previewStack = stack);
        } finally {
            owner.suppressNotify = false;
        }
    }


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
        }
    }


    static boolean isMachineOwned(int slot) {
        return slot >= BlockEntityArcaneAssembler.PATTERN_SLOT_START
                        && slot < BlockEntityArcaneAssembler.GEAR_SLOT_START
                || slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                        && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START;
    }

    static boolean isDisplaySlot(int slot) {
        return slot == BlockEntityArcaneAssembler.TARGET_SLOT
                || slot >= BlockEntityArcaneAssembler.PREVIEW_SLOT_START
                        && slot < BlockEntityArcaneAssembler.UPGRADE_SLOT_START;
    }

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