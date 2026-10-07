package thaumicenergistics_ce.client.gui;

import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaTerminal;

/**
 * 源质终端的界面：整体沿用 AE2 的终端，另加两个自己的手势。
 * 右键把手持的罐或药瓶倒空进网络，左键点击条目则把罐或药瓶装满；按住
 * shift 则取走整叠手持物品，shift 右键则在容器所在处倒空。
 * 除此之外，手持容器和其他物品一样放入，只有手势认领点击的地方例外。
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
