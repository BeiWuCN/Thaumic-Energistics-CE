package thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * 源质终端的菜单，由线缆部件和无线物品共用。
 * 常规的一切都来自 AE2 的 {@link MEStorageMenu}，源质只增加了它的键
 * 类型。源质搬运继承自 {@link MenuEssentiaTerminalBase}，且只作用于玩家
 * 手持的容器，而提供哪些键类型由宿主的 [KeyTypeSelection] 决定。
 */
public class MenuEssentiaTerminal extends MenuEssentiaTerminalBase {

    public MenuEssentiaTerminal(MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }
}
