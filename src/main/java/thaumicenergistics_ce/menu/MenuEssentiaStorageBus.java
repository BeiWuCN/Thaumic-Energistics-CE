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
 * 源质存储总线的配置界面。
 * <ul>
 * <li>网格是分区不是过滤器：它列出网络可以在这个总线的容器里保留什么。
 * <li>网格与槽位寻址出自 {@link MenuEssentiaBusBase}，JEI 也读它，玩家和总线因此不会对不上。
 * </ul>
 */
public class MenuEssentiaStorageBus extends MenuEssentiaBusBase<PartEssentiaStorageBus>
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
     * 不读任何设置：覆写不是优化。继承来的版本无条件读模糊模式设置，
     * 而这个宿主没注册任何设置——打开时会抛 {@code UnsupportedSettingException}。
     */
    @Override
    protected void loadSettingsFromHost(IConfigManager configManager) {
        // 故意留空：这个宿主没注册设置，继承来的版本没得读。
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
