package thaumicenergistics_ce.menu;

import appeng.api.stacks.AEKeyType;
import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.menu.guisync.GuiSync;
import appeng.menu.interfaces.KeyTypeSelectionMenu;
import appeng.util.ConfigInventory;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import thaumicenergistics_ce.part.PartEssentiaExportBus;

/**
 * The Essentia Export Bus's config screen: AE2's upgradeable-bus menu, with the config grid and its
 * slot addressing inherited from {@link MenuEssentiaBus} - the same screen as the import bus's, the
 * only difference being what each bus does with the config, not how it is presented.
 */
public class MenuEssentiaExportBus extends MenuEssentiaBus<PartEssentiaExportBus>
        implements KeyTypeSelectionMenu {

    @GuiSync(20)
    public SyncedKeyTypes exportKeyTypes = new SyncedKeyTypes();

    public MenuEssentiaExportBus(MenuType<?> menuType, int id, Inventory playerInventory, PartEssentiaExportBus host) {
        super(menuType, id, playerInventory, host);
    }

    @Override
    protected ConfigInventory configInventory() {
        return getHost().getConfig();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (isServerSide() && getHost() instanceof KeyTypeSelectionHost selectionHost) {
            Map<AEKeyType, Boolean> enabled = selectionHost.getKeyTypeSelection().enabled();
            if (!exportKeyTypes.keyTypes().equals(enabled)) {
                exportKeyTypes = new SyncedKeyTypes(enabled);
            }
        }
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        return ((KeyTypeSelectionHost) getHost()).getKeyTypeSelection();
    }

    @Override
    public SyncedKeyTypes getClientKeyTypeSelection() {
        return exportKeyTypes;
    }
}
