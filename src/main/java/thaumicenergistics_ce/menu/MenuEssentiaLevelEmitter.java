package thaumicenergistics_ce.menu;

import appeng.api.config.Settings;
import appeng.api.util.IConfigManager;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.UpgradeableMenu;
import appeng.menu.slot.FakeSlot;
import appeng.util.ConfigMenuInventory;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import thaumicenergistics_ce.part.PartEssentiaLevelEmitter;

/**
 * The Essentia Level Emitter's screen.
 *
 * <p>One config slot holding the aspect to watch, and a number the player sets. The number travels as a
 * client action rather than through a synced field, which is how AE2's own level emitter does it: a
 * setting the player types is a command, and a command that also arrives from the server would fight with
 * what the player is typing.
 *
 * <p>The reporting value is sent once when the menu opens, so the box shows what the emitter is actually
 * set to rather than a default.
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

    /** The network's current total for the watched aspect, for the screen to show. */
    public long getCurrentLevel() {
        return getHost().getCurrentLevel();
    }

    /**
     * Sets the threshold.
     *
     * <p>Called on the client by the screen, which forwards it to the server as a client action; called on
     * the server by that action, which is where the emitter is actually changed.
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
    public net.minecraft.resources.ResourceLocation getConfiguredAspect() {
        var key = getHost().getConfiguredKey();
        return key == null ? null : key.getId();
    }
}
