package thaumicenergistics_ce.menu;

import appeng.api.storage.ITerminalHost;
import appeng.menu.slot.AppEngSlot;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import thaumicenergistics_ce.item.WirelessArcaneCraftingTerminalMenuHost;
import thaumicenergistics_ce.recipe.arcane.TerminalAuraPayment;
import thaumicenergistics_ce.registry.TECEItems;

final class ArcaneTerminalUpgrades {
    private final ITerminalHost host;
    private final @Nullable WirelessArcaneCraftingTerminalMenuHost wireless;

    ArcaneTerminalUpgrades(ITerminalHost host) {
        this.host = host;
        wireless = host instanceof WirelessArcaneCraftingTerminalMenuHost linked ? linked : null;
    }

    boolean wireless() { return wireless != null; }

    void updateVisibility(List<Slot> slots, boolean essentiaEnabled) {
        int hidden = -1;
        if (!essentiaEnabled) {
            int cardSlots = wireless() ? Math.min(2, slots.size()) : slots.size();
            for (int i = cardSlots - 1; i >= 0; i--) {
                var slot = (AppEngSlot) slots.get(i);
                if (slot.getInventory().getStackInSlot(slot.getContainerSlot()).isEmpty()) {
                    hidden = i;
                    break;
                }
            }
        }
        for (int i = 0; i < slots.size(); i++) ((AppEngSlot) slots.get(i)).setSlotEnabled(i != hidden);
    }

    boolean hasEssentiaCard() {
        return host.getUpgrades().isInstalled(TECEItems.ESSENTIA_ACCESS_CARD.get());
    }
    ItemStack visCard() { return TerminalAuraPayment.visCard(host); }
}
