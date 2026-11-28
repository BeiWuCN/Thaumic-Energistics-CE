package com.beiwu.thaumicenergistics_ce.menu;

import appeng.api.config.Settings;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import com.beiwu.thaumicenergistics_ce.menu.slot.EssentiaFilterSlot;
import com.beiwu.thaumicenergistics_ce.ThEIds;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import appeng.util.ConfigMenuInventory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import com.beiwu.thaumicenergistics_ce.part.PartEssentiaLevelEmitter;

/**
 * 源质标准发信器的界面：一个放要素的配置槽，加一个可设的数字。数字是客户端动作不是同步字段，
 * 因玩家敲的是一条指令；上报值在菜单打开时发一次，输入框才显示真实值。
 */
public class MenuEssentiaLevelEmitter extends UpgradeableMenu<PartEssentiaLevelEmitter> {

    private static final String ACTION_SET_REPORTING_VALUE = "setReportingValue";
    private static final String ACTION_SET_FILTER = "setFilter";
    /** 清空过滤：客户端动作的取值类型是 ResourceLocation，发不出 null。 */
    private static final ResourceLocation CLEAR_FILTER = ThEIds.id("clear");

    private long reportingValue;

    public MenuEssentiaLevelEmitter(
            MenuType<?> menuType, int id, Inventory playerInventory, PartEssentiaLevelEmitter host) {
        super(menuType, id, playerInventory, host);
        registerClientAction(ACTION_SET_REPORTING_VALUE, Long.class, this::setValue);
        registerClientAction(ACTION_SET_FILTER, ResourceLocation.class, this::setFilter);
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

    /**
     * 设过滤的要素：客户端转一条客户端动作，服务端动作写进配置槽。
     * JEI 拖拽走这里——它手上是要素配料而不是容器物品，点槽位那条路不收。
     */
    public void setFilter(ResourceLocation aspectId) {
        if (isClientSide()) {
            sendClientAction(ACTION_SET_FILTER, aspectId == null ? CLEAR_FILTER : aspectId);
            return;
        }
        if (aspectId == null || CLEAR_FILTER.equals(aspectId)) {
            getHost().getConfig().setStack(0, null);
            return;
        }
        var aspect = AEssentiaKeyType.aspectOf(getPlayer().level(), aspectId);
        if (aspect == null) {
            // 要素被数据包删了：别把一条界面改不动的配置写进去。
            return;
        }
        getHost().getConfig().setStack(0, new GenericStack(AEssentiaKey.of(aspect), 1));
    }

    /** 服务端在菜单发出前调用，客户端的输入框从真实值开始。 */
    public void setInitialValue(long value) {
        reportingValue = value;
    }

    @Override
    protected void setupConfig() {
        ConfigMenuInventory config = getHost().getConfig().createMenuWrapper();
        addSlot(new EssentiaFilterSlot(config, 0), SlotSemantics.CONFIG);
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