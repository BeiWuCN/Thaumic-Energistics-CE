package thaumicenergistics_ce.menu;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigMenuInventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;

/**
 * 源质标准发信器的屏幕：一个用于要素的配置槽，外加一个可设定的数字。
 * 这个数字是客户端动作而非同步字段，因为玩家输入的内容是一条
 * 指令；上报值在菜单打开时发送一次，使输入框显示真实
 * 值。
 */
public class MenuEssentiaLevelEmitter extends UpgradeableMenu<PartEssentiaLevelEmitter> {

    private static final String ACTION_SET_REPORTING_VALUE = "setReportingValue";

    private long reportingValue;

    public MenuEssentiaLevelEmitter(
            MenuType<?> menuType, int id, Inventory playerInventory, PartEssentiaLevelEmitter host) {
        super(menuType, id, playerInventory, host);
        registerClientAction(ACTION_SET_REPORTING_VALUE, Long.class, this::setValue);
    }

    public long getReportingValue() {
        return reportingValue;
    }

    public long getCurrentLevel() {
        return getHost().getCurrentLevel();
    }

    /** 设置阈值：客户端转发一条客户端动作，由服务端动作应用它。 */
    public void setValue(long value) {
        if (isClientSide()) {
            reportingValue = value;
            sendClientAction(ACTION_SET_REPORTING_VALUE, value);
        } else {
            reportingValue = value;
            getHost().setReportingValue(value);
        }
    }

    /** 由服务端在菜单发送前调用，使客户端的输入框从真实值开始。 */
    public void setInitialValue(long value) {
        reportingValue = value;
    }

    @Override
    protected void setupConfig() {
        ConfigMenuInventory config = getHost().getConfig().createMenuWrapper();
        addSlot(new FakeSlot(config, 0), SlotSemantics.CONFIG);
    }

    @Override
    protected void loadSettingsFromHost(IConfigManager settings) {
        setRedStoneMode(settings.getSetting(Settings.REDSTONE_EMITTER));
    }

    public ResourceLocation getConfiguredAspect() {
        var key = getHost().getConfiguredKey();
        return key == null ? null : key.getId();
    }
}
