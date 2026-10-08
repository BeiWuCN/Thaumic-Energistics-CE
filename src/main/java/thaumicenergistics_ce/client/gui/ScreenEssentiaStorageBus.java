package thaumicenergistics_ce.client.gui;

import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics_ce.menu.MenuEssentiaStorageBus;

/**
 * 源质存储总线的屏幕。
 * <ul>
 *   <li>单独命名，因为 JEI 的幽灵物品处理器按屏幕类注册。
 *   <li>沿用 AE2 的 {@code UpgradeableScreen} 没动：它的配置网格曾是唯一一个
 *       拖不进要素的网格。
 * </ul>
 */
public class ScreenEssentiaStorageBus extends UpgradeableScreen<MenuEssentiaStorageBus> {

    public ScreenEssentiaStorageBus(
            MenuEssentiaStorageBus menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }
}
