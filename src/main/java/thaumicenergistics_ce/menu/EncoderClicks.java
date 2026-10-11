package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

final class EncoderClicks {

    private EncoderClicks() {}

    static boolean handles(
            MenuDistillationEncoder host, int slotId, int dragType, ContainerInput clickType, Player player) {
        host.table.ensure();
        if (slotId == MenuDistillationEncoder.MENU_SOURCE) {
            if (player.level().isClientSide()) {
                ItemStack carried = host.getCarried();
                host.requestSourceTemplate(carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return true;
        }
        int aspectStart = MenuDistillationEncoder.MENU_ASPECT_START;
        int aspectSlots = MenuDistillationEncoder.ASPECT_SLOTS;
        if (slotId >= aspectStart && slotId < aspectStart + aspectSlots) {
            int index = slotId - aspectStart;
            if (index < host.table.aspectCount() && host.table.isRevealed(index)) {
                host.table.select(index);
                if (player.level().isClientSide()) {
                    host.sendAction(MenuNetwork.ACTION_SELECT, index);
                } else if (host.encoder != null) {
                    host.encoder.setSelectedAspect(index);
                }
            }
            return true;
        }
        if (slotId == MenuDistillationEncoder.MENU_SELECTED) {
            host.table.select(-1);
            if (player.level().isClientSide()) {
                host.sendAction(MenuNetwork.ACTION_SELECT, -1);
            } else if (host.encoder != null) {
                host.encoder.setSelectedAspect(-1);
            }
            return true;
        }
        return false;
    }
}
