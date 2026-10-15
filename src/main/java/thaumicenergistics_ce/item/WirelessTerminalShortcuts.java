package thaumicenergistics_ce.item;

import appeng.hotkeys.HotkeyActions;
import appeng.hotkeys.InventoryHotkeyAction;
import appeng.hotkeys.CuriosHotkeyAction;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.init.ModItems;

public final class WirelessTerminalShortcuts {
    public static final String ESSENTIA = "thaumicenergistics_ce.wireless_essentia_terminal";
    public static final String ARCANE = "thaumicenergistics_ce.wireless_arcane_crafting_terminal";

    private WirelessTerminalShortcuts() {}

    public static void register() {
        register(ESSENTIA, stack -> stack.is(ModItems.WIRELESS_ESSENTIA_TERMINAL.get()),
                (player, locator) -> ModItems.WIRELESS_ESSENTIA_TERMINAL.get().openFromInventory(player, locator));
        register(ARCANE, stack -> stack.is(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()),
                (player, locator) -> ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get().openFromInventory(player, locator));
    }

    private static void register(String id, Predicate<ItemStack> item, InventoryHotkeyAction.Opener opener) {
        HotkeyActions.register(new InventoryHotkeyAction(item, opener), id);
        HotkeyActions.register(new CuriosHotkeyAction(item, opener), id);
    }
}
