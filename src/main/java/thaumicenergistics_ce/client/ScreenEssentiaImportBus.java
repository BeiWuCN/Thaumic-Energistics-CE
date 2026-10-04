package thaumicenergistics_ce.client;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaImportBus;

/**
 * The Essentia Import Bus's screen; named rather than using AE2's {@code UpgradeableScreen}
 * directly, because JEI registers its ghost ingredient handler against a screen class and an
 * anonymous or generic screen cannot offer one.
 */
public class ScreenEssentiaImportBus extends UpgradeableScreen<MenuEssentiaImportBus> {

    public ScreenEssentiaImportBus(
            MenuEssentiaImportBus menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
