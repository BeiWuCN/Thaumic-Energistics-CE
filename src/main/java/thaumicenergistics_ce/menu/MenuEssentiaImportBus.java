package thaumicenergistics_ce.menu;

import appeng.api.util.KeyTypeSelection;
import appeng.api.util.KeyTypeSelectionHost;
import appeng.menu.guisync.GuiSync;
import appeng.menu.interfaces.KeyTypeSelectionMenu;
import appeng.util.ConfigInventory;
import java.util.Map;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import thaumicenergistics_ce.part.PartEssentiaImportBus;

/**
 * The Essentia Import Bus's config screen.
 *
 * <p>AE2's own upgradeable-bus menu, with the config grid and slot addressing inherited from
 * {@link MenuEssentiaBus}. Two things are this bus's own.
 *
 * <p>The config grid is <em>expandable</em> - two rows always shown, five more unlocked by capacity
 * cards - which is AE2's behaviour for its own buses.
 *
 * <p>The key types are synced so the client's "which types does this bus accept" checkbox knows what the
 * part decided. The part answers essentia and nothing else, so this is reported rather than chosen - but
 * a bus that offered items it cannot move would be worse than no setting at all, and this is the channel
 * AE2 uses to say so.
 */
public class MenuEssentiaImportBus extends MenuEssentiaBus<PartEssentiaImportBus>
        implements KeyTypeSelectionMenu {

    @GuiSync(20)
    public SyncedKeyTypes importKeyTypes = new SyncedKeyTypes();

    public MenuEssentiaImportBus(MenuType<?> menuType, int id, Inventory playerInventory, PartEssentiaImportBus host) {
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
            Map<appeng.api.stacks.AEKeyType, Boolean> enabled = selectionHost.getKeyTypeSelection().enabled();
            if (!importKeyTypes.keyTypes().equals(enabled)) {
                importKeyTypes = new SyncedKeyTypes(enabled);
            }
        }
    }

    @Override
    public KeyTypeSelection getServerKeyTypeSelection() {
        return ((KeyTypeSelectionHost) getHost()).getKeyTypeSelection();
    }

    @Override
    public SyncedKeyTypes getClientKeyTypeSelection() {
        return importKeyTypes;
    }
}
