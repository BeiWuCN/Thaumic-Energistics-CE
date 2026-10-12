package thaumicenergistics_ce.item;

import appeng.api.storage.ILinkStatus;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.recipe.arcane.ArcaneTerminalHost;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;

public class WirelessArcaneCraftingTerminalMenuHost
        extends QuantumWirelessTerminalMenuHost<ItemWirelessArcaneCraftingTerminal> implements ArcaneTerminalHost {

    public WirelessArcaneCraftingTerminalMenuHost(
            ItemWirelessArcaneCraftingTerminal item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    public void migrateSharedCards() {
        if (getPlayer().level().isClientSide()) return;
        var bound = arcaneTerminal();
        if (bound == null) return;
        var local = getUpgrades();
        var target = bound.getUpgrades();
        for (int i = 0; i < local.size(); i++) {
            var card = local.getStackInSlot(i);
            if (!card.is(thaumicenergistics_ce.registry.TECEItems.ESSENTIA_ACCESS_CARD.get())
                    && !card.is(thaumicenergistics_ce.registry.TECEItems.VIS_CONNECTION_CARD.get())) continue;
            for (int j = 0; j < target.size(); j++) {
                if (!target.insertItem(j, card, true).isEmpty()) continue;
                var removed = local.extractItem(i, 1, false);
                var remaining = target.insertItem(j, removed, false);
                if (!remaining.isEmpty()) local.insertItem(i, remaining, false);
                break;
            }
        }
    }

    public boolean hasUnmigratedSharedCards() {
        if (arcaneTerminal() == null) return false;
        for (var stack : getUpgrades()) {
            if (stack.is(thaumicenergistics_ce.registry.TECEItems.VIS_CONNECTION_CARD.get())
                    || stack.is(thaumicenergistics_ce.registry.TECEItems.ESSENTIA_ACCESS_CARD.get())) return true;
        }
        return false;
    }

    public void returnConflictingCards() {
        if (getPlayer().level().isClientSide() || arcaneTerminal() == null) return;
        var local = getUpgrades();
        for (int i = 0; i < local.size(); i++) {
            var stack = local.getStackInSlot(i);
            if (!stack.is(thaumicenergistics_ce.registry.TECEItems.VIS_CONNECTION_CARD.get())
                    && !stack.is(thaumicenergistics_ce.registry.TECEItems.ESSENTIA_ACCESS_CARD.get())) continue;
            var returned = stack.copy();
            getPlayer().getInventory().add(returned);
            int accepted = stack.getCount() - returned.getCount();
            if (accepted > 0) local.extractItem(i, accepted, false);
        }
    }

    public void arrangeLocalQuantumCard() {
        if (getPlayer().level().isClientSide() || !QuantumTerminalSupport.enabled()) return;
        var local = getUpgrades();
        if (local.size() < 3 || !local.getStackInSlot(2).isEmpty()) return;
        for (int i = 0; i < 2; i++) {
            var stack = local.getStackInSlot(i);
            if (!stack.is(QuantumTerminalSupport.card())) continue;
            var card = local.extractItem(i, 1, false);
            var remainder = local.insertItem(2, card, false);
            if (!remainder.isEmpty()) local.insertItem(i, remainder, false);
            return;
        }
    }

    @Override
    public ILinkStatus getLinkStatus() {
        ILinkStatus status = super.getLinkStatus();
        if (status.connected() && arcaneTerminal() == null) {
            status = ILinkStatus.ofDisconnected(net.minecraft.network.chat.Component.translatable(
                    "gui.thaumicenergistics_ce.wireless_arcane_crafting_terminal.not_bound"));
        }
        return status;
    }

    @Override
    public @Nullable PartArcaneCraftingTerminal arcaneTerminal() {
        var part = ItemWirelessArcaneCraftingTerminal.pairedTerminal(getPlayer().level(), getItemStack(), quantumConnected());
        var connected = getActionableNode();
        var bound = part == null ? null : part.getGridNode();
        return connected != null && bound != null && connected.getGrid() == bound.getGrid() ? part : null;
    }

}
