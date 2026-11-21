package com.beiwu.thaumicenergistics_ce.item;

import appeng.hotkeys.HotkeyActions;
import appeng.hotkeys.InventoryHotkeyAction;
import appeng.hotkeys.CuriosHotkeyAction;
import java.util.function.Predicate;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;

public final class WirelessTerminalShortcuts {
    public static final String ESSENTIA = "thaumicenergistics_ce.wireless_essentia_terminal";
    public static final String ARCANE = "thaumicenergistics_ce.wireless_arcane_crafting_terminal";
    public static final String ENCODING = "thaumicenergistics_ce.wireless_arcane_encoding_terminal";

    private WirelessTerminalShortcuts() {}

    public static void register() {
        register(ESSENTIA, stack -> stack.is(TECEItems.WIRELESS_ESSENTIA_TERMINAL.get()),
                (player, locator) -> TECEItems.WIRELESS_ESSENTIA_TERMINAL.get().openFromInventory(player, locator));
        // 两个奥术终端都可能是两种实现（独立 / AE2WTLib），热键只管能打开界面就行：
        // 两边都继承 AE2 的无线终端基类，openFromInventory 在基类上。
        register(ARCANE, stack -> stack.is(TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                (player, locator) -> openWireless(TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), player, locator));
        register(ENCODING, stack -> stack.is(TECEItems.WIRELESS_ARCANE_ENCODING_TERMINAL.get()),
                (player, locator) -> openWireless(TECEItems.WIRELESS_ARCANE_ENCODING_TERMINAL.get(), player, locator));
    }

    private static boolean openWireless(Item item, net.minecraft.world.entity.player.Player player,
            appeng.menu.locator.ItemMenuHostLocator locator) {
        return item instanceof appeng.items.tools.powered.WirelessTerminalItem wireless
                && wireless.openFromInventory(player, locator);
    }

    private static void register(String id, Predicate<ItemStack> item, InventoryHotkeyAction.Opener opener) {
        HotkeyActions.register(new InventoryHotkeyAction(item, opener), id);
        HotkeyActions.register(new CuriosHotkeyAction(item, opener), id);
    }
}
