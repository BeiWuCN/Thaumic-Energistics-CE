package thaumicenergistics_ce.client;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaExportBus;

/**
 * The Essentia Export Bus's screen.
 *
 * <p>A named class for the same reason as the import bus's: JEI's ghost ingredient handler registers
 * against a screen class.
 */
public class ScreenEssentiaExportBus extends UpgradeableScreen<MenuEssentiaExportBus> {

    public ScreenEssentiaExportBus(
            MenuEssentiaExportBus menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
