package thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;

public class MenuEssentiaTerminal extends MenuEssentiaTerminalBase {

    public MenuEssentiaTerminal(MenuType<?> menuType, int id, Inventory playerInventory, ITerminalHost host) {
        super(menuType, id, playerInventory, host);
    }
}
