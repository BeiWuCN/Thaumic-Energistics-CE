package thaumicenergistics_ce.item;

import appeng.api.inventories.InternalInventory;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.items.contents.StackDependentSupplier;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.SupplierInternalInventory;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemContainerContents;
import thaumicenergistics_ce.init.TECEDataComponents;

public class WirelessArcaneCraftingTerminalMenuHost
        extends QuantumWirelessTerminalMenuHost<ItemWirelessArcaneCraftingTerminal> {
    private final InternalInventory items;

    public WirelessArcaneCraftingTerminalMenuHost(ItemWirelessArcaneCraftingTerminal item, Player player,
            ItemMenuHostLocator locator, BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        items = new SupplierInternalInventory<>(new StackDependentSupplier<>(this::getItemStack, stack -> {
            var inventory = new AppEngInternalInventory(new InternalInventoryHost() {
                @Override
                public void saveChangedInventory(AppEngInternalInventory changed) {
                    stack.set(TECEDataComponents.WIRELESS_ARCANE_ITEMS.get(), changed.toItemContainerContents());
                }
                @Override
                public boolean isClientSide() { return player.level().isClientSide(); }
            }, 16);
            inventory.fromItemContainerContents(stack.getOrDefault(
                    TECEDataComponents.WIRELESS_ARCANE_ITEMS.get(), ItemContainerContents.EMPTY));
            return inventory;
        }));
    }

    public InternalInventory craftingGrid() { return items.getSubInventory(0, 9); }
    public InternalInventory crystalInventory() { return items.getSubInventory(9, 15); }
    public InternalInventory wandInventory() { return items.getSubInventory(15, 16); }
}
