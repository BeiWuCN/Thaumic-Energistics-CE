package thaumicenergistics_ce.item;

import appeng.api.util.KeyTypeSelection;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;

/**
 * The menu host behind the Wireless Essentia Terminal.
 *
 * <ul>
 * <li>Everything a wireless terminal's host does - connecting to the linked network, paying the
 * power cost, the range check - comes from AE2's {@link WirelessTerminalMenuHost}. The one override
 * is the key types the terminal offers: the whole difference between this and an ME terminal.</li>
 * <li>Read through the part-based {@code PartEssentiaTerminal}, which makes the same restriction
 * the same way: two terminals that both say "essentia only" should say it through the same
 * mechanism, or one of them eventually stops saying it.</li>
 * </ul>
 */
public class WirelessEssentiaTerminalMenuHost extends WirelessTerminalMenuHost<ItemWirelessEssentiaTerminal> {

    /**
     * Essentia only.
     *
     * The listener is empty because there is nothing to save: the host answers the same thing every
     * time, so a "the player changed the selection" callback has nothing to record.
     */
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
