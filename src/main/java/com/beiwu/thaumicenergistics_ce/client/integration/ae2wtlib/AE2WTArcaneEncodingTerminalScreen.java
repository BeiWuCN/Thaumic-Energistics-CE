package com.beiwu.thaumicenergistics_ce.client.integration.ae2wtlib;

import appeng.client.gui.style.ScreenStyle;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenArcaneEncodingTerminal;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneEncodingTerminal;

/**
 * 装了 AE2WTLib 时的无线奥术编码终端界面：
 * 编码底板、知识核心样板面板、三个开关按钮全部继承面板版，
 * 外加 AE2WTLib 的升级卡面板、终端切换按钮与热键，口径与
 * {@link AE2WTArcaneCraftingTerminalScreen} 完全一致。
 */
public class AE2WTArcaneEncodingTerminalScreen extends ScreenArcaneEncodingTerminal
        implements IUniversalTerminalCapable {

    private ScrollingUpgradesPanel upgradesPanel;

    public AE2WTArcaneEncodingTerminalScreen(
            MenuArcaneEncodingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        if (menu.getHost() instanceof WTMenuHost host && host.getItemStack().getItem() instanceof ItemWUT) {
            addToLeftToolbar(cycleTerminalButton());
        }
    }

    @Override
    public void init() {
        // 同合成终端：滚动面板要在 super.init() 前就位（populateScreen 在 init 里跑），
        // AE2 那份 UpgradesPanel（构造器里就加了）则在 super.init() 后摘掉，remove 幂等。
        if (this.upgradesPanel == null) {
            this.upgradesPanel = addUpgradePanel(widgets, getMenu());
        }
        super.init();
        ((com.beiwu.thaumicenergistics_ce.mixin.WidgetContainerAccessor) widgets)
                .tce$compositeWidgets()
                .remove("upgrades");
        this.upgradesPanel.setMaxRows(Math.max(2, getVisibleRows()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return checkForTerminalKeys(keyCode, scanCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public WTMenuHost getHost() {
        return getMenu().getHost() instanceof WTMenuHost host ? host : null;
    }
}
