package thaumicenergistics_ce.item;

import appeng.api.util.KeyTypeSelection;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * 无线源质终端背后的菜单宿主。
 * 全部终端行为都来自 AE2 的 {@link WirelessTerminalMenuHost}，唯一的覆写是
 * 按键。它们通过基于部件的 {@code PartEssentiaTerminal} 读取，后者以同样的
 * 方式施加同样的限制：两个都宣称 "essentia only" 的终端应当通过同一个
 * 机制来宣称。
 */
public class WirelessEssentiaTerminalMenuHost extends WirelessTerminalMenuHost<ItemWirelessEssentiaTerminal> {

    /** 仅源质。监听器为空是因为没有任何东西需要保存：宿主每次都回答同样的
     * 内容，所以 "the player changed the selection" 回调没有任何可记录的东西。 */
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
