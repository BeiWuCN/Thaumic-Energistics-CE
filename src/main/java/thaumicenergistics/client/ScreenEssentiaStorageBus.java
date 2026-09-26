package thaumicenergistics.client;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics.menu.MenuEssentiaStorageBus;

/**
 * The Essentia Storage Bus's screen.
 *
 * <p>A named class for the same reason as the other two buses': JEI's ghost ingredient handler registers
 * against a screen class, and a generic screen has none to register against. Leaving this one on AE2's
 * {@code UpgradeableScreen} directly is why its config grid was the one grid an aspect could not be
 * dragged into.
 */
public class ScreenEssentiaStorageBus extends UpgradeableScreen<MenuEssentiaStorageBus> {

    public ScreenEssentiaStorageBus(
            MenuEssentiaStorageBus menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
