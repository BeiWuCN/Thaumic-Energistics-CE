package thaumicenergistics_ce.menu;

import appeng.core.definitions.AEItems;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The distillation encoder's action bodies, split out of {@link MenuDistillationEncoder} to keep it
 * inside the file's line budget. Every method is a forward target of the host and only ever runs on the
 * host instance it is handed. */
final class EncoderActions {

    private EncoderActions() {}

    static void selectAspect(MenuDistillationEncoder host, int index) {
        if (index < -1 || index >= host.table.aspectCount()) {
            return;
        }
        // Refused here and not only in the screen: an action payload arrives through this path too, and a
        // hand-assembled click must not select an undiscovered aspect.
        if (index >= 0 && !host.table.isRevealed(index)) {
            return;
        }
        host.table.select(index);
        if (host.encoder != null) {
            host.encoder.setSelectedAspect(index);
            host.table.refresh();
        }
    }

    static void encode(MenuDistillationEncoder host) {
        if (host.encoder != null) {
            host.encoder.encode();
            host.table.refresh();
        }
    }

    static void applySourceTemplate(MenuDistillationEncoder host, ItemStack stack) {
        ItemStack wanted = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        host.slots.get(MenuDistillationEncoder.MENU_SOURCE).set(wanted);
        host.table.select(-1);
        host.table.refresh();
    }

    static void requestSourceTemplate(MenuDistillationEncoder host, ItemStack stack) {
        ItemStack wanted = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        host.slots.get(MenuDistillationEncoder.MENU_SOURCE).set(wanted);
        host.table.select(-1);
        host.table.refresh();
        MenuNetwork.sendEncoderSource(host.containerId, wanted);
    }

    static void insertBlank(MenuDistillationEncoder host, Player player) {
        if (!host.slots.get(MenuDistillationEncoder.MENU_BLANK).getItem().isEmpty()) {
            return;
        }
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (AEItems.BLANK_PATTERN.is(stack)) {
                ItemStack one = stack.copyWithCount(1);
                stack.shrink(1);
                inventory.setChanged();
                host.slots.get(MenuDistillationEncoder.MENU_BLANK).set(one);
                host.table.refresh();
                return;
            }
        }
    }

    static boolean canEncode(MenuDistillationEncoder host) {
        if (host.slots.get(MenuDistillationEncoder.MENU_SOURCE).getItem().isEmpty()) {
            return false;
        }
        if (host.table.pickedIndex() < 0) {
            return false;
        }
        ItemStack blank = host.slots.get(MenuDistillationEncoder.MENU_BLANK).getItem();
        if (blank.isEmpty() || !AEItems.BLANK_PATTERN.is(blank)) {
            return false;
        }
        return host.slots.get(MenuDistillationEncoder.MENU_ENCODED).getItem().isEmpty();
    }
}
