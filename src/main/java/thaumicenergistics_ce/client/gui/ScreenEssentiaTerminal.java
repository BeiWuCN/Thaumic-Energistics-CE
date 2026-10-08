package thaumicenergistics_ce.client.gui;

import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;

/**
 * 源质终端的界面：整体用 AE2 的终端，另加两个自己的手势。
 * 右键把手持的罐或药瓶倒进网络，左键点条目装满一个；按 shift 取走整叠手持物，
 * shift 右键在容器所在处倒空。别的手持容器按普通物品放入，只有手势认领点击处例外。
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
