package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;

/** The distillation encoder's click handling and shift-click routing, split out of
 * {@link MenuDistillationEncoder} to keep it inside the file's line budget. */
final class EncoderClicks {

    private EncoderClicks() {}

    static boolean handles(
            MenuDistillationEncoder host, int slotId, int dragType, ClickType clickType, Player player) {
        // The row is re-derived first, so the decision is made against it as it is now.
        host.table.ensure();
        // Menu indices, not container ones: slotId indexes this menu's list, players' slots first. The
        // source well is handled here too, as TemplateSlot refuses both ways and so cannot be emptied.
        if (slotId == MenuDistillationEncoder.MENU_SOURCE) {
            if (player.level().isClientSide) {
                ItemStack carried = host.getCarried();
                host.requestSourceTemplate(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return true;
        }
        int aspectStart = MenuDistillationEncoder.MENU_ASPECT_START;
        int aspectSlots = MenuDistillationEncoder.ASPECT_SLOTS;
        if (slotId >= aspectStart && slotId < aspectStart + aspectSlots) {
            int index = slotId - aspectStart;
            // Nothing is drawn for an undiscovered well, so a click where nothing is drawn must not pick.
            if (index < host.table.aspectCount() && host.table.isRevealed(index)) {
                host.table.select(index);
                if (player.level().isClientSide) {
                    host.sendAction(MenuNetwork.ACTION_SELECT, index);
                } else if (host.encoder != null) {
                    host.encoder.setSelectedAspect(index);
                }
            }
            return true;
        }
        if (slotId == MenuDistillationEncoder.MENU_SELECTED) {
            // Clicking the picked aspect clears it.
            host.table.select(-1);
            if (player.level().isClientSide) {
                host.sendAction(MenuNetwork.ACTION_SELECT, -1);
            } else if (host.encoder != null) {
                host.encoder.setSelectedAspect(-1);
            }
            return true;
        }
        return false;
    }
}
