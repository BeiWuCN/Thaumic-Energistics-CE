package thaumicenergistics_ce.item;

import appeng.api.ids.AEComponents;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.Upgrades;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.fml.ModList;
import thaumicenergistics_ce.init.TECEDataComponents;
import thaumicenergistics_ce.init.ModItems;

public final class QuantumTerminalSupport {
    private QuantumTerminalSupport() {}

    public static boolean enabled() {
        return ModList.get().isLoaded("ae2wtlib");
    }

    public static Item card() {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2wtlib", "quantum_bridge_card"));
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
        stack.getOrDefault(TECEDataComponents.QUANTUM_SINGULARITY.get(), ItemContainerContents.EMPTY).copyInto(items);
        return items.getFirst();
    }

    public static boolean validSingularity(ItemStack stack) {
        return stack.is(appeng.core.definitions.AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem())
                && stack.has(AEComponents.ENTANGLED_SINGULARITY_ID);
    }

    public static java.util.function.Consumer<net.minecraft.network.chat.Component> connectionTooltip(
            ItemStack stack, java.util.function.Consumer<net.minecraft.network.chat.Component> lines) {
        boolean quantumLinked = enabled() && validSingularity(singularity(stack))
                && stack.getItem() instanceof appeng.api.upgrades.IUpgradeableItem item
                && item.getUpgrades(stack).isInstalled(card());
        return line -> {
            boolean linked = line.getString().equals(appeng.core.localization.GuiText.Linked.text().getString());
            boolean unlinked = line.getString().equals(appeng.core.localization.GuiText.Unlinked.text().getString());
            if (linked || (unlinked && quantumLinked)) {
                lines.accept(net.minecraft.network.chat.Component.translatable(
                        "item.thaumicenergistics_ce.wireless_terminal.connected")
                        .setStyle(appeng.core.localization.Tooltips.GREEN));
            } else {
                lines.accept(line);
            }
        };
    }
}
