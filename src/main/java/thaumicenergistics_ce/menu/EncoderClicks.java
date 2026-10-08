package thaumicenergistics_ce.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

/** 蒸馏编码器的点击处理与 shift 点击路由。
 * 从 {@link MenuDistillationEncoder} 拆出，以守住文件行数预算。 */
final class EncoderClicks {

    private EncoderClicks() {}

    static boolean handles(
            MenuDistillationEncoder host, int slotId, int dragType, ContainerInput clickType, Player player) {
        // 先重新推导这一行，后面的判断依据它当前的样子。
        host.table.ensure();
        // slotId 是菜单索引不是容器索引，玩家槽位排在前面。
        // 源凹槽也在这里处理：TemplateSlot 两个方向都拒绝，否则清不掉。
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
            // 未发现的凹槽不绘制内容，在那个位置点击不选中。
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
            // 点击已选中的要素会清除它。
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
