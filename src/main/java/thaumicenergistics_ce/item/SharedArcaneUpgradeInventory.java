package thaumicenergistics_ce.item;

import appeng.api.inventories.InternalInventory;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.SupplierInternalInventory;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.registry.TECEItems;

public final class SharedArcaneUpgradeInventory extends SupplierInternalInventory<InternalInventory> {
    public SharedArcaneUpgradeInventory(WirelessArcaneCraftingTerminalMenuHost host) {
        super(delegate(host));
    }

    private static java.util.function.Supplier<InternalInventory> delegate(WirelessArcaneCraftingTerminalMenuHost host) {
        var client = new AppEngInternalInventory(null, 2, 1);
        var original = host.getPlayer().level().isClientSide() ? null : host.arcaneTerminal();
        var unavailable = new AppEngInternalInventory(null, 2, 1);
        unavailable.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
            @Override public boolean allowInsert(InternalInventory inv, int slot, ItemStack stack) { return false; }
        });
        return () -> {
            if (host.getPlayer().level().isClientSide()) return client;
            var bound = host.arcaneTerminal();
            if (bound != original || (bound != null && host.hasUnmigratedSharedCards())) return unavailable;
            return bound == null ? host.getUpgrades().getSubInventory(0, 2) : bound.getUpgrades();
        };
    }

    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return (stack.is(TECEItems.VIS_CONNECTION_CARD.get())
                || (ThEConfig.ARCANE_ESSENTIA_ACCESS.get() && stack.is(TECEItems.ESSENTIA_ACCESS_CARD.get())))
                && super.isItemValid(slot, stack);
    }
    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        return isItemValid(slot, stack) ? super.insertItem(slot, stack, simulate) : stack;
    }
}
