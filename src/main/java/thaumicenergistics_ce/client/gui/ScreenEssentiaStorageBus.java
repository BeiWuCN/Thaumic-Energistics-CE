package thaumicenergistics_ce.client.gui;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaStorageBus;

/**
 * The Essentia Storage Bus's screen.
 * It exists as a named class because JEI's ghost ingredient handler registers against a screen
 * class; left on AE2's UpgradeableScreen, its config grid was the one grid an aspect could not be
 * dragged into.
 */
public class ScreenEssentiaStorageBus extends UpgradeableScreen<MenuEssentiaStorageBus> {

    public ScreenEssentiaStorageBus(
            MenuEssentiaStorageBus menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
