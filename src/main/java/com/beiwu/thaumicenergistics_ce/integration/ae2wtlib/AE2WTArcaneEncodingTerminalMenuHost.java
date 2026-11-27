package com.beiwu.thaumicenergistics_ce.integration.ae2wtlib;

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
import com.beiwu.thaumicenergistics_ce.registry.TECEDataComponents;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

/**
 * 通用终端里奥术编码模式的宿主：九格编码网格与知识核心存在终端物品组件里，
 * 选中项也是物品上的一个整数，合上界面再打开还在。
 *
 * <p>与独立实现共用同一份组件（{@link TECEDataComponents#WIRELESS_ARCANE_ENCODING_GRID} 等），
 * 界面、按钮、编码逻辑全部复用 {@code MenuArcaneEncodingTerminal}；
 * 模式切换、升级卡与量子桥由 {@link WTMenuHost} 提供。
 */
public class AE2WTArcaneEncodingTerminalMenuHost extends WTMenuHost implements ArcaneEncodingStorage {

    private final InternalInventory grid;

    private final InternalInventory core;

    public AE2WTArcaneEncodingTerminalMenuHost(
            ItemWT item,
            Player player,
            ItemMenuHostLocator locator,
            BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
        this.grid = container(TECEDataComponents.WIRELESS_ARCANE_ENCODING_GRID.get(), 9, player, false);
        this.core = container(TECEDataComponents.WIRELESS_ARCANE_ENCODING_CORE.get(), 1, player, true);
    }

    /** 每个物品堆一份库存，写回时把整份倒进组件；每次从物品堆重建，换核换终端都不串。 */
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
        return getItemStack().getOrDefault(
                TECEDataComponents.WIRELESS_ARCANE_ENCODING_SELECTED.get(), -1);
    }

    @Override
    public void setSelectedEncodingPattern(int index) {
        getItemStack().set(TECEDataComponents.WIRELESS_ARCANE_ENCODING_SELECTED.get(), index);
    }
}
