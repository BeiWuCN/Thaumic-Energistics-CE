package thaumicenergistics_ce.client;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaImportBus;

/**
 * The Essentia Import Bus's screen.
 *
 * <p>A named class rather than AE2's {@code UpgradeableScreen} used directly, for one reason: JEI's ghost
 * ingredient handler is registered against a screen class, and an anonymous or generic screen has no
 * class to register against. Everything it does comes from the parent.
 */
public class ScreenEssentiaImportBus extends UpgradeableScreen<MenuEssentiaImportBus> {

    public ScreenEssentiaImportBus(
            MenuEssentiaImportBus menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
