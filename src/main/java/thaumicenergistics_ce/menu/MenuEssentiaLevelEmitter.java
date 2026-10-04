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
 * The Essentia Level Emitter's screen: one config slot for the aspect, plus a settable number.
 *
 * <ul>
 *   <li>The number is a client action, not a synced field: what the player types is a command.
 *   <li>The reporting value is sent once when the menu opens, so the box shows the real value.
 * </ul>
 */
public class MenuEssentiaLevelEmitter extends UpgradeableMenu<PartEssentiaLevelEmitter> {

    private static final String ACTION_SET_REPORTING_VALUE = "setReportingValue";

    private long reportingValue;

    public MenuEssentiaLevelEmitter(
            MenuType<?> menuType, int id, Inventory playerInventory, PartEssentiaLevelEmitter host) {
        super(menuType, id, playerInventory, host);
        registerClientAction(ACTION_SET_REPORTING_VALUE, Long.class, this::setValue);
    }

    /** The value the player has set, on either side. */
    public long getReportingValue() {
        return reportingValue;
    }

    /** The network's total for the watched aspect, for the screen to show. */
    public long getCurrentLevel() {
        return getHost().getCurrentLevel();
    }

    /**
     * Sets the threshold: the client forwards a client action, the server action applies it.
     */
    public void setValue(long value) {
        if (isClientSide()) {
            reportingValue = value;
            sendClientAction(ACTION_SET_REPORTING_VALUE, value);
        } else {
            reportingValue = value;
            getHost().setReportingValue(value);
        }
    }

    /** Called by the server before the menu is sent, so the client's box starts at the real value. */
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

    /** The aspect the player picked, for the screen's label. */
    public ResourceLocation getConfiguredAspect() {
        var key = getHost().getConfiguredKey();
        return key == null ? null : key.getId();
    }
}
