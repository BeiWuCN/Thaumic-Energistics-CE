package thaumicenergistics_ce.item;

import appeng.api.util.KeyTypeSelection;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * 无线源质终端背后的菜单宿主。终端行为全来自 AE2 的 {@link WirelessTerminalMenuHost}，
 * 唯一的覆写是按键。按键经基于部件的 {@code PartEssentiaTerminal} 读，
 * 后者用同样方式施加同样的限制：两个都说「只要源质」的终端该走同一套机制。
 */
public class WirelessEssentiaTerminalMenuHost extends WirelessTerminalMenuHost<ItemWirelessEssentiaTerminal> {

    /** 只要源质。监听器为空是因没有东西要存：宿主每次都答同一句，
     * 「玩家改了选择」这类回调没有可记的。 */
    private final KeyTypeSelection essentiaOnly =
            new KeyTypeSelection(() -> {}, keyType -> keyType == AEssentiaKeyType.INSTANCE);

    public WirelessEssentiaTerminalMenuHost(
            ItemWirelessEssentiaTerminal item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return essentiaOnly;
    }
}
