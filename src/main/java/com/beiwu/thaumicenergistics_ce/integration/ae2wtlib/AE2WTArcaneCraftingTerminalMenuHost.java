package com.beiwu.thaumicenergistics_ce.integration.ae2wtlib;

import appeng.api.inventories.InternalInventory;
import appeng.items.contents.StackDependentSupplier;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.SupplierInternalInventory;
import java.util.function.BiConsumer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.ItemContainerContents;
import com.beiwu.thaumicenergistics_ce.item.WirelessArcaneCraftingHost;
import com.beiwu.thaumicenergistics_ce.registry.TECEDataComponents;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

/**
 * 通用终端里奥术合成模式的宿主：九格网格、六个水晶槽与法杖槽存在终端物品组件里，
 * 与独立无线终端同一份数据组件（{@link TECEDataComponents#WIRELESS_ARCANE_ITEMS}），
 * 两种实现互换时玩家的东西不会留在原处。
 *
 * <p>其余一切白拿 AE2WTLib 的 {@link WTMenuHost}：模式切换、升级卡库存、奇点库存、
 * 量子桥连接与充电。本类不重复实现任何一项，否则会出现双库存与重复扣费。
 */
public class AE2WTArcaneCraftingTerminalMenuHost extends WTMenuHost implements WirelessArcaneCraftingHost {

    private final InternalInventory items;

    public AE2WTArcaneCraftingTerminalMenuHost(
            ItemWT item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        this.items = new SupplierInternalInventory<>(new StackDependentSupplier<>(this::getItemStack, stack -> {
            var inventory = new AppEngInternalInventory(new InternalInventoryHost() {
                @Override
                public void saveChangedInventory(AppEngInternalInventory changed) {
                    stack.set(TECEDataComponents.WIRELESS_ARCANE_ITEMS.get(), changed.toItemContainerContents());
                }

                @Override
                public boolean isClientSide() {
                    return player.level().isClientSide();
                }
            }, 16);
            inventory.fromItemContainerContents(stack.getOrDefault(
                    TECEDataComponents.WIRELESS_ARCANE_ITEMS.get(), ItemContainerContents.EMPTY));
            return inventory;
        }));
    }

    @Override
    public InternalInventory craftingGrid() {
        return items.getSubInventory(0, 9);
    }

    @Override
    public InternalInventory crystalInventory() {
        return items.getSubInventory(9, 15);
    }

    @Override
    public InternalInventory wandInventory() {
        return items.getSubInventory(15, 16);
    }
}
