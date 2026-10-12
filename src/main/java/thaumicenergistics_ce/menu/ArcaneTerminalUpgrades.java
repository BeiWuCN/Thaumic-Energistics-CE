package thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import appeng.menu.slot.AppEngSlot;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.client.gui.ArcaneUpgradeSlotBinding;
import thaumicenergistics_ce.item.SharedArcaneUpgradeInventory;
import thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost;
import thaumicenergistics_ce.part.PartArcaneCraftingTerminal;
import thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.registry.TECEItems;

/** Owns shared upgrade binding and card lookup; the menu owns synchronization. */
final class ArcaneTerminalUpgrades {
    private final ITerminalHost host;
    private final @Nullable WirelessArcaneCraftingTerminalMenuHost wireless;

    ArcaneTerminalUpgrades(ITerminalHost host, List<Slot> slots) {
        this.host = host;
        wireless = host instanceof WirelessArcaneCraftingTerminalMenuHost linked ? linked : null;
        if (wireless != null) {
            prepare();
            var shared = new SharedArcaneUpgradeInventory(wireless);
            for (int i = 0; i < Math.min(2, slots.size()); i++) {
                ((ArcaneUpgradeSlotBinding) slots.get(i)).bindSharedInventory(shared);
            }
        }
    }

    boolean shared() { return wireless != null; }

    boolean prepare() {
        if (wireless == null) return true;
        wireless.migrateSharedCards();
        wireless.returnConflictingCards();
        wireless.arrangeLocalQuantumCard();
        return !wireless.hasUnmigratedSharedCards();
    }

    boolean connectionUnchanged(@Nullable PartArcaneCraftingTerminal original) {
        return wireless == null || wireless.arcaneTerminal() == original;
    }

    void updateVisibility(List<Slot> slots, boolean essentiaEnabled) {
        int hidden = -1;
        if (!essentiaEnabled) {
            int cardSlots = shared() ? Math.min(2, slots.size()) : slots.size();
            for (int i = cardSlots - 1; i >= 0; i--) {
                var slot = (AppEngSlot) slots.get(i);
                // Disabled slots report no item; inspect storage to reveal remotely inserted cards.
                if (slot.getInventory().getStackInSlot(slot.getContainerSlot()).isEmpty()) {
                    hidden = i;
                    break;
                }
            }
        }
        for (int i = 0; i < slots.size(); i++) ((AppEngSlot) slots.get(i)).setSlotEnabled(i != hidden);
    }

    boolean hasEssentiaCard() {
        var bound = wireless == null ? null : wireless.arcaneTerminal();
        return (bound == null ? host.getUpgrades() : bound.getUpgrades())
                .isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get());
    }

    ItemStack inheritedVisCard() {
        var bound = wireless == null ? null : wireless.arcaneTerminal();
        return bound == null ? ItemStack.EMPTY : TerminalAuraPayment.visCard(bound).copy();
    }

    ItemStack visCard() {
        var inherited = inheritedVisCard();
        return inherited.isEmpty() ? TerminalAuraPayment.visCard(host) : inherited;
    }
}
