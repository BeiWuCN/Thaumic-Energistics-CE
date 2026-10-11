package thaumicenergistics_ce.item;

import appeng.api.ids.AEComponents;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.fml.ModList;
import thaumicenergistics_ce.registry.ModDataComponents;
import thaumicenergistics_ce.registry.ModItems;

public final class QuantumTerminalSupport {
    private QuantumTerminalSupport() {}

    public static boolean enabled() {
        return ModList.get().isLoaded("ae2wtlib");
    }

    public static Item card() {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("ae2wtlib", "quantum_bridge_card"));
    }

    public static void registerUpgrades() {
        if (!enabled() || card() == null || card() == Items.AIR) return;
        Upgrades.add(card(), ModItems.WIRELESS_ESSENTIA_TERMINAL.get(), 1);
        Upgrades.add(card(), ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get(), 1);
    }

    public static IUpgradeInventory upgrades(ItemStack stack, appeng.api.upgrades.ItemUpgradesChanged changed) {
        return UpgradeInventories.forItem(stack, enabled() ? 3 : 2, changed);
    }

    public static ItemStack singularity(ItemStack stack) {
        var items = net.minecraft.core.NonNullList.withSize(1, ItemStack.EMPTY);
        stack.getOrDefault(ModDataComponents.QUANTUM_SINGULARITY.get(), ItemContainerContents.EMPTY).copyInto(items);
        return items.getFirst();
    }

    public static boolean validSingularity(ItemStack stack) {
        return stack.is(appeng.core.definitions.AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem())
                && stack.has(AEComponents.ENTANGLED_SINGULARITY_ID);
    }
}
