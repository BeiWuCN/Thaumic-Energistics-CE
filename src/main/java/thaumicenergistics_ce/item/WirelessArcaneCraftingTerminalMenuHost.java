package thaumicenergistics_ce.item;

import appeng.api.storage.ILinkStatus;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.BiConsumer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

/**
 * The menu host behind the Wireless Arcane Crafting Terminal: AE2's wireless terminal host, plus
 * the one thing AE2 has no notion of - the placed terminal this item was paired with. Answering
 * {@link ArcaneTerminalHost} is what makes the menu build the placed terminal's own grid,
 * wand slot and crystals, so both screens show one state rather than two copies.
 */
public class WirelessArcaneCraftingTerminalMenuHost
        extends WirelessTerminalMenuHost<ItemWirelessArcaneCraftingTerminal> implements ArcaneTerminalHost {

    public WirelessArcaneCraftingTerminalMenuHost(
            ItemWirelessArcaneCraftingTerminal item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    @Override
    public @Nullable PartArcaneCraftingTerminal arcaneTerminal() {
        return ItemWirelessArcaneCraftingTerminal.pairedTerminal(getPlayer().level(), getItemStack());
    }

    /**
     * A terminal that is on a network but paired with no placed terminal says so where the screen already
     * says "not connected": the item is the one thing AE2's own link status cannot know about.
     */
    @Override
    public ILinkStatus getLinkStatus() {
        ILinkStatus status = super.getLinkStatus();
        if (!status.connected() || arcaneTerminal() != null) {
            return status;
        }
        return ILinkStatus.ofDisconnected(Component.translatable(
                "gui.thaumicenergistics_ce.wireless_arcane_crafting_terminal.not_bound"));
    }
}
