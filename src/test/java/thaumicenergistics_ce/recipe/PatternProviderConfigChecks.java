package thaumicenergistics_ce.recipe;

import appeng.api.config.Actionable;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.leclowndu93150.thaumaturge.api.aspect.IAspect;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaPatternProvider;
import thaumicenergistics_ce.registry.TECEItems;

final class PatternProviderConfigChecks {
    static void run(PatternProviderLogicHost host, PatternProviderTarget target, net.minecraft.core.Holder<IAspect> aspect) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(
                (net.minecraft.server.level.ServerLevel) host.getBlockEntity().getLevel());
        var menu = new appeng.menu.implementations.PatternProviderMenu(
                appeng.menu.implementations.PatternProviderMenu.TYPE, 77, player.getInventory(), host);
        run(host, target, aspect, menu);
    }

    static void run(PatternProviderLogicHost host, PatternProviderTarget target, net.minecraft.core.Holder<IAspect> aspect,
            appeng.menu.implementations.PatternProviderMenu menu) {
        var uiState = (thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaProviderMenuState) menu;
        var namespace = host instanceof appeng.parts.AEBasePart
                ? BuiltInRegistries.ITEM.getKey(host.getTerminalIcon().getItem()).getNamespace()
                : BuiltInRegistries.BLOCK.getKey(host.getBlockEntity().getBlockState().getBlock()).getNamespace();
        var own = switch (namespace) {
            case "ae2" -> ThEConfig.PATTERN_PROVIDER_AE2;
            case "extendedae" -> ThEConfig.PATTERN_PROVIDER_EXTENDED_AE;
            case "extendedae_plus" -> ThEConfig.PATTERN_PROVIDER_EXTENDED_AE_PLUS;
            default -> throw new IllegalStateException("Unexpected provider: " + namespace);
        };
        var ext = (EssentiaPatternProvider) host.getLogic();
        var inv = ext.essentiaCardInventory();
        var receiver = ext.essentiaReturnStorage();
        var values = java.util.List.of(ThEConfig.PATTERN_PROVIDER_AE2,
                ThEConfig.PATTERN_PROVIDER_EXTENDED_AE, ThEConfig.PATTERN_PROVIDER_EXTENDED_AE_PLUS);
        for (var value : values) {
            boolean previous = value.get();
            try {
                value.set(false);
                if (value != own) {
                    require(ext.essentiaEnabled(), "Other provider switch affected " + namespace);
                    continue;
                }
                require(!ext.essentiaEnabled(), "Switch did not disable " + namespace);
                menu.broadcastChanges();
                require(!uiState.essentiaSupportEnabled(), "Open menu did not hot-disable UI support");
                var original = java.util.List.of(appeng.core.localization.GuiText.CompatibleUpgrades.text(),
                        appeng.core.localization.GuiText.CompatibleUpgrade.text(
                                TECEItems.ESSENTIA_ACCESS_CARD.get().getDefaultInstance().getItemName(), 1));
                require(thaumicenergistics_ce.integration.ae2.patternprovider.ProviderUpgradeTooltip.apply(
                        original, uiState.essentiaSupportEnabled()).isEmpty(), "Disabled tooltip retained essentia card/header");

                require(inv.getStackInSlot(0).getCount() == 1, "Disabled switch lost card");
                require(target.insert(AEssentiaKey.of(aspect), 1, Actionable.MODULATE) == 0,
                        "Cached adapter bypassed disabled switch");
                try (var tx = Transaction.openRoot()) {
                    require(receiver.insert(aspect, 1, tx) == 0, "Cached receiver bypassed switch");
                }
                require(host.getLogic().getReturnInv().insert(AEssentiaKey.of(aspect), 1,
                        Actionable.MODULATE, appeng.api.networking.security.IActionSource.empty()) == 0,
                        "Native return inventory bypassed switch");
                var card = inv.extractItem(0, 1, false);
                require(card.getCount() == 1, "Disabled switch trapped card");
                require(inv.insertItem(0, card, false).getCount() == 1, "Disabled switch accepted card");
                value.set(true);
                menu.broadcastChanges();
                require(uiState.essentiaSupportEnabled(), "Same open menu did not hot-enable support");
                var enabled = thaumicenergistics_ce.integration.ae2.patternprovider.ProviderUpgradeTooltip.apply(
                        java.util.List.of(), uiState.essentiaSupportEnabled());
                require(enabled.size() == 2 && thaumicenergistics_ce.integration.ae2.patternprovider.ProviderUpgradeTooltip.apply(
                        enabled, true).size() == 2, "Enabled tooltip omitted or duplicated the essentia card");
                require(inv.insertItem(0, card, false).isEmpty() && ext.essentiaEnabled(),
                        "Re-enabling did not restore card support");
            } finally { value.set(previous); }
        }
        System.out.println("[pattern-provider-config] PASS: " + namespace
                + " independent switches, same open menu hot-toggle and tooltip filtering, cached send/receive gates, card preservation/removal and re-enable");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
