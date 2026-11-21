package com.beiwu.thaumicenergistics_ce.item;

import appeng.api.inventories.InternalInventory;
import appeng.items.contents.StackDependentSupplier;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import appeng.util.inv.SupplierInternalInventory;
import appeng.util.inv.filter.IAEItemFilter;
import java.util.function.BiConsumer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import com.beiwu.thaumicenergistics_ce.menu.ArcaneEncodingStorage;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import com.beiwu.thaumicenergistics_ce.registry.TECEDataComponents;

/**
 * 无线奥术编码终端的菜单宿主：九格编码网格和知识核心都存在物品组件里，
 * 选中项也是物品上的一个整数，合上界面再打开还在。
 *
 * <p>和面板版的区别只在这层存储：界面、按钮、编码逻辑全部复用
 * {@link com.beiwu.thaumicenergistics_ce.menu.MenuArcaneEncodingTerminal}。
 */
public class WirelessArcaneEncodingTerminalMenuHost
        extends QuantumWirelessTerminalMenuHost<ItemWirelessArcaneEncodingTerminal>
        implements ArcaneEncodingStorage {

    /** 和部件版一致：九格网格 + 一个核心槽。 */
    private final InternalInventory grid;

    private final InternalInventory core;

    public WirelessArcaneEncodingTerminalMenuHost(
            ItemWirelessArcaneEncodingTerminal item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        this.grid = container(TECEDataComponents.WIRELESS_ARCANE_ENCODING_GRID.get(), 9, player, false);
        this.core = container(TECEDataComponents.WIRELESS_ARCANE_ENCODING_CORE.get(), 1, player, true);
    }

    /**
     * 每个物品堆一份库存，写回时把整份倒进组件。
     * 每次从物品堆重建，所以换核、换终端都不会串。
     */
    private InternalInventory container(
            DataComponentType<ItemContainerContents> type, int size, Player player, boolean coresOnly) {
        return new SupplierInternalInventory<>(new StackDependentSupplier<>(this::getItemStack, stack -> {
            var inventory = new AppEngInternalInventory(new InternalInventoryHost() {
                @Override
                public void saveChangedInventory(AppEngInternalInventory changed) {
                    stack.set(type, changed.toItemContainerContents());
                }

                @Override
                public boolean isClientSide() {
                    return player.level().isClientSide();
                }
            }, size);
            inventory.fromItemContainerContents(stack.getOrDefault(type, ItemContainerContents.EMPTY));
            if (coresOnly) {
                inventory.setFilter(new IAEItemFilter() {
                    @Override
                    public boolean allowInsert(InternalInventory inv, int slot, ItemStack candidate) {
                        return candidate.is(TECEItems.KNOWLEDGE_CORE.get());
                    }
                });
            }
            return inventory;
        }));
    }

    @Override
    public InternalInventory encodingGrid() {
        return grid;
    }

    @Override
    public InternalInventory encodingCore() {
        return core;
    }

    @Override
    public int selectedEncodingPattern() {
        return getItemStack().getOrDefault(TECEDataComponents.WIRELESS_ARCANE_ENCODING_SELECTED.get(), -1);
    }

    @Override
    public void setSelectedEncodingPattern(int index) {
        getItemStack().set(TECEDataComponents.WIRELESS_ARCANE_ENCODING_SELECTED.get(), index);
    }
}
