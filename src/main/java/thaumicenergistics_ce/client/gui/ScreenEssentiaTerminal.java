package thaumicenergistics_ce.client.gui;

import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;

/**
 * The Essentia Terminal's screen: AE2's terminal wholesale, plus two gestures of its own.
 * <ul>
 *   <li>Right-click empties a held jar or phial into the network, left-click an entry fills one.
 *   <li>Shift takes the whole held stack, shift-right-click empties where the container lies.
 *   <li>A held container goes in like any other item, except where a gesture claims the click.
 * </ul>
 */
public class ScreenEssentiaTerminal extends ScreenEssentiaTerminalBase<MenuEssentiaTerminal> {

    public ScreenEssentiaTerminal(
            MenuEssentiaTerminal menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    @Override
    protected boolean essentiaGesturesAtAll() {
        return true;
    }
}
