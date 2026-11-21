package com.beiwu.thaumicenergistics_ce.item;

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
import com.beiwu.thaumicenergistics_ce.registry.TECEDataComponents;

/**
 * 不走 AE2WTLib 时的无线奥术合成终端宿主：工作台九格、水晶与法杖各占一段，
 * 全部存在物品自己的数据组件里。
 *
 * <p>装了 AE2WTLib 时换用继承 {@code WTMenuHost} 的版本，那边白拿量子桥与升级卡库存；
 * 两者实现同一个 {@link WirelessArcaneCraftingHost}，菜单不必知道差别。</p>
 */
public class WirelessArcaneCraftingTerminalMenuHost
        extends QuantumWirelessTerminalMenuHost<ItemWirelessArcaneCraftingTerminal>
        implements WirelessArcaneCraftingHost {

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

    @Override
    public InternalInventory craftingGrid() { return items.getSubInventory(0, 9); }

    @Override
    public InternalInventory crystalInventory() { return items.getSubInventory(9, 15); }

    @Override
    public InternalInventory wandInventory() { return items.getSubInventory(15, 16); }
}
