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
 * 源质标准发信器的界面：一个放要素的配置槽，加一个可设的数字。数字是客户端动作不是同步字段，
 * 因玩家敲的是一条指令；上报值在菜单打开时发一次，输入框才显示真实值。
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

    /** 设阈值：客户端转一条客户端动作，服务端动作应用它。 */
    public void setValue(long value) {
        if (isClientSide()) {
            reportingValue = value;
            sendClientAction(ACTION_SET_REPORTING_VALUE, value);
        } else {
            reportingValue = value;
            getHost().setReportingValue(value);
        }
    }

    /** 服务端在菜单发出前调用，客户端的输入框从真实值开始。 */
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
