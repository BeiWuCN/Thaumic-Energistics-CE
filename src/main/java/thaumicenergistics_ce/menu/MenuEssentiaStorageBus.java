package thaumicenergistics_ce.menu;

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
 *
 * <p>The config grid here is a partition rather than a filter: what it lists is what the network is
 * allowed to keep in the container the bus faces. The grid and its slot addressing come from
 * {@link MenuEssentiaBus}, which JEI also reads - so the grid a player drags onto and the grid the bus
 * writes to cannot drift apart.
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
     * Loads nothing, because this bus has no settings to load.
     *
     * <p>Overriding this is not an optimisation - the inherited version reads the fuzzy-mode setting
     * unconditionally, and this bus's host never registers one. {@code UpgradeablePart} registers no
     * settings at all; only the IO buses add redstone and fuzzy mode of their own accord. So opening a
     * storage bus asked its config manager for a setting that was never registered, which threw
     * {@code UnsupportedSettingException} out of {@code broadcastChanges} on the first slot update and
     * broke the screen the moment it opened.
     *
     * <p>AE2's own {@code StorageBusMenu} overrides this for the same reason, though it has settings of
     * its own to read instead. An essentia partition has nothing of the sort to read: the entries are
     * aspects, and aspects have no damage values or NBT for a fuzzy match to widen, so there is
     * deliberately no fuzzy setting here to keep in sync.
     */
    @Override
    protected void loadSettingsFromHost(IConfigManager configManager) {
        // Intentionally empty - see above.
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide() && getHost() instanceof KeyTypeSelectionHost selectionHost) {
            Map<appeng.api.stacks.AEKeyType, Boolean> enabled = selectionHost.getKeyTypeSelection().enabled();
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
