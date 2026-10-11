package thaumicenergistics_ce.client.gui;

import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;

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
