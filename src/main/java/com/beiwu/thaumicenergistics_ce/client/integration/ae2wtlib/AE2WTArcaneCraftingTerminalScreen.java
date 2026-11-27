package com.beiwu.thaumicenergistics_ce.client.integration.ae2wtlib;

import appeng.client.gui.style.ScreenStyle;
import de.mari_023.ae2wtlib.api.gui.ScrollingUpgradesPanel;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import de.mari_023.ae2wtlib.api.terminal.IUniversalTerminalCapable;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import com.beiwu.thaumicenergistics_ce.client.gui.ScreenArcaneCraftingTerminal;
import com.beiwu.thaumicenergistics_ce.menu.MenuArcaneCraftingTerminal;

/**
 * 装了 AE2WTLib 时的无线奥术合成终端界面：布局、按钮、vis 读数全部继承面板版，
 * 多出来的只有 AE2WTLib 那一套——升级卡滚动面板、终端切换按钮、终端热键。
 *
 * <p>三样都由 {@link IUniversalTerminalCapable} 的默认实现提供，本类负责挂上去，
 * 并在按键时先问一句热键。顺序与 AE2WTLib 自己的无线终端一致：先挂按钮与升级面板，
 * 再走父类的 {@code init()} 铺开布局。
 *
 * <p>只在 {@link AE2WTClientIntegration} 里创建：本类引用可选依赖的类，
 * 缺 AE2WTLib 时这份字节码不该被加载。
 */
public class AE2WTArcaneCraftingTerminalScreen extends ScreenArcaneCraftingTerminal
        implements IUniversalTerminalCapable {

    private ScrollingUpgradesPanel upgradesPanel;

    public AE2WTArcaneCraftingTerminalScreen(
            MenuArcaneCraftingTerminal menu, Inventory inventory, Component title, ScreenStyle style) {
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
