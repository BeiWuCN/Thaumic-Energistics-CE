package com.beiwu.thaumicenergistics_ce.client.gui;

import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.client.gui.NumberEntryType;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.client.gui.widgets.ServerSettingToggleButton;
import appeng.client.gui.widgets.SettingToggleButton;
import com.beiwu.thaumicenergistics_ce.menu.MenuEssentiaLevelEmitter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * 源质发信器的界面：阈值输入框加红石模式开关。
 * <p>样式文档只画得出静态控件，数字框和模式按钮是 AE2 的界面类在 Java 里加的，所以这里照
 * {@code EnergyLevelEmitterScreen} 的做法补一遍，不这么写界面上就只剩下那个配置槽。
 * <p>要素靠往配置槽里塞一只装着源质的安瓿/罐来指定，见 {@code EssentiaFilterSlot}。
 */
public class ScreenEssentiaLevelEmitter extends UpgradeableScreen<MenuEssentiaLevelEmitter> {

    private final SettingToggleButton<RedstoneMode> redstoneMode =
            new ServerSettingToggleButton<>(Settings.REDSTONE_EMITTER, RedstoneMode.LOW_SIGNAL);

    private final NumberEntryWidget level;

    public ScreenEssentiaLevelEmitter(
            MenuEssentiaLevelEmitter menu, Inventory playerInventory, Component title, ScreenStyle style) {
        super(menu, playerInventory, title, style);
        addToLeftToolbar(redstoneMode);
        level = widgets.addNumberEntryWidget("level", NumberEntryType.UNITLESS);
        level.setTextFieldStyle(style.getWidget("levelInput"));
        level.setLongValue(menu.getReportingValue());
        level.setOnChange(this::saveReportingValue);
        level.setOnConfirm(this::onClose);
    }

    @Override
    protected void updateBeforeRender() {
        super.updateBeforeRender();
        redstoneMode.active = true;
        redstoneMode.set(menu.getRedStoneMode());
    }

    private void saveReportingValue() {
        level.getLongValue().ifPresent(menu::setValue);
    }
}
