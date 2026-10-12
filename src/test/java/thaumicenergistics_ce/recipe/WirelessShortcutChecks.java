package thaumicenergistics_ce.recipe;

import appeng.hotkeys.CuriosHotkeyAction;
import appeng.hotkeys.HotkeyActions;
import appeng.hotkeys.InventoryHotkeyAction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics_ce.registry.TECEItems;
import thaumicenergistics_ce.item.WirelessTerminalShortcuts;

public final class WirelessShortcutChecks {
    private WirelessShortcutChecks() {}

    public static void run() {
        check(WirelessTerminalShortcuts.ESSENTIA, TECEItems.WIRELESS_ESSENTIA_TERMINAL.get(),
                TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get());
        check(WirelessTerminalShortcuts.ARCANE, TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(),
                TECEItems.WIRELESS_ESSENTIA_TERMINAL.get());
        System.out.println("[wireless-shortcuts] PASS: both curio tags and native inventory/curio actions; terminal types remain distinct");
    }

    private static void check(String id, Item item, Item other) {
        var stack = new ItemStack(item);
        if (!stack.is(TagKey.create(Registries.ITEM, Identifier.parse("curios:curio")))) {
            throw new IllegalStateException("Wireless terminal is missing its curio tag");
        }
        var actions = HotkeyActions.REGISTRY.get(id);
        if (actions == null || actions.size() != 2
                || actions.stream().noneMatch(a -> a instanceof InventoryHotkeyAction inventory
                        && inventory.locatable().test(stack) && !inventory.locatable().test(new ItemStack(other)))
                || actions.stream().noneMatch(a -> a instanceof CuriosHotkeyAction curios
                        && curios.locatable().test(stack) && !curios.locatable().test(new ItemStack(other)))) {
            throw new IllegalStateException("Missing or incorrect native wireless terminal shortcut actions: " + id);
        }
    }
}
