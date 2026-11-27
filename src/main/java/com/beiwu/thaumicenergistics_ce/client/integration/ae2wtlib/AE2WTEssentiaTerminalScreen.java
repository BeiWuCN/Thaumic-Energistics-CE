package com.beiwu.thaumicenergistics_ce.client.integration.ae2wtlib;

import appeng.client.gui.style.ScreenStyle;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenEssentiaTerminal;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaTerminal;

public class AE2WTEssentiaTerminalScreen extends ScreenEssentiaTerminal
        implements IUniversalTerminalCapable {

    private ScrollingUpgradesPanel upgradesPanel;

    public AE2WTEssentiaTerminalScreen(
            MenuEssentiaTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
        // 挂在构造器里而不是 init()：init() 会随每次 resize 重复调用，而工具栏是纯追加的表。
        // 也只在通用终端里打开时才有得切，判据照 AE2WTLib：宿主物品是不是通用终端。
        if (menu.getHost() instanceof WTMenuHost host && host.getItemStack().getItem() instanceof ItemWUT) {
            addToLeftToolbar(cycleTerminalButton());
        }
    }

    @Override
    public void init() {
        // 滚动面板必须赶在 super.init() 前就位：AEBaseScreen.init 里的
        // widgets.populateScreen 会把当时 map 里的面板逐个落位并 populate，
        // 晚加的则要等到下一次 init 才归位。
        if (this.upgradesPanel == null) {
            this.upgradesPanel = addUpgradePanel(widgets, getMenu());
        }
        super.init();
        // 摘掉 AE2 自己挂的那份 UpgradesPanel（MEStorageScreen 构造器里就加了）。
        // 两个面板都会在 updateBeforeRender 里直接写 UPGRADE 槽的 x/y，
        // 留着就变成每帧互相覆盖、槽落在窗口外。放在 super.init() 之后是为了
        // 保证首次开屏就已摘掉：本例首次 init 以前面板已在 map 里，remove 幂等。
        ((com.beiwu.thaumicenergistics_ce.mixin.WidgetContainerAccessor) widgets)
                .tce$compositeWidgets()
                .remove("upgrades");
        this.upgradesPanel.setMaxRows(Math.max(2, getVisibleRows()));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 终端热键（切换终端、无线设置）优先于普通按键。
        return checkForTerminalKeys(keyCode, scanCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public WTMenuHost getHost() {
        return getMenu().getHost() instanceof WTMenuHost host ? host : null;
    }
}
