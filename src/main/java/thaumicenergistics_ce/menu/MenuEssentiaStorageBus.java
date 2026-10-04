package thaumicenergistics_ce.menu;

import appeng.api.stacks.AEKeyType;
import appeng.api.util.IConfigManager;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.menu.guisync.GuiSync;
import appeng.menu.interfaces.KeyTypeSelectionMenu;
import appeng.util.ConfigInventory;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import thaumicenergistics_ce.part.PartEssentiaStorageBus;

/**
 * The Essentia Storage Bus's config screen.
 * <ul>
 * <li>The grid is a partition, not a filter: it lists what the network may keep in the bus's container.
 * <li>Grid and slot addressing come from {@link MenuEssentiaBus}, which JEI also reads, so player
 *     and bus cannot drift apart.
 * </ul>
 */
public class MenuEssentiaStorageBus extends MenuEssentiaBus<PartEssentiaStorageBus>
        implements KeyTypeSelectionMenu {

    @GuiSync(20)
    public SyncedKeyTypes storageKeyTypes = new SyncedKeyTypes();

    public MenuEssentiaStorageBus(
            MenuType<?> menuType, int id, Inventory playerInventory, PartEssentiaStorageBus host) {
        super(menuType, id, playerInventory, host);
    }

    @Override
    protected ConfigInventory configInventory() {
        return getHost().getConfig();
    }

    /**
     * Loads nothing: overriding is not an optimisation. The inherited version reads the fuzzy-mode setting
     * unconditionally, and this host registers none - that threw {@code UnsupportedSettingException} on open.
     */
    @Override
    protected void loadSettingsFromHost(IConfigManager configManager) {
        // Intentionally empty: this host registers no setting for the inherited version to read.
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide() && getHost() instanceof KeyTypeSelectionHost selectionHost) {
            Map<AEKeyType, Boolean> enabled = selectionHost.getKeyTypeSelection().enabled();
            if (!storageKeyTypes.keyTypes().equals(enabled)) {
                storageKeyTypes = new SyncedKeyTypes(enabled);
            }
        }
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        return ((KeyTypeSelectionHost) getHost()).getKeyTypeSelection();
    }

    @Override
    public SyncedKeyTypes getClientKeyTypeSelection() {
        return storageKeyTypes;
    }
}
