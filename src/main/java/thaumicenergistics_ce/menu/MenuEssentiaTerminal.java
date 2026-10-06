package thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

/**
 * The Essentia Terminal's menu, shared by the cable part and the wireless item.
 * <ul>
 *   <li>Everything ordinary comes from AE2's {@link MEStorageMenu}; essentia only adds its key type.
 *   <li>Moving essentia is inherited from {@link MenuEssentiaTerminalBase} and acts only on the
 *       container the player holds; which key types are offered is the host's {@code KeyTypeSelection}.
 * </ul>
 */
public class MenuEssentiaTerminal extends MenuEssentiaTerminalBase {

    public MenuEssentiaTerminal(MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }
}
