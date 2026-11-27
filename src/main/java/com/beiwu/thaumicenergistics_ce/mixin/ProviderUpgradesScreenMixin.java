package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.api.upgrades.Upgrades;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.ICompositeWidget;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import com.beiwu.thaumicenergistics_ce.client.gui.EssentiaUpgradeTooltip;
import com.beiwu.thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaProviderMenuState;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class ProviderUpgradesScreenMixin {
    @Shadow @Final protected WidgetContainer widgets;
    @Unique private ICompositeWidget tce$boundUpgradePanel;

    // Applied Flux installs its panel in the constructor; Data Energistics does so before super.init().
    // At this point both have had their turn and AE2 has yet to lay out the widgets.
    @Inject(method = "init()V", at = @At("HEAD"))
    private void tce$prepareProviderUpgrades(CallbackInfo ci) {
        var menu = ((AEBaseScreen<?>) (Object) this).getMenu();
        if (!(menu instanceof EssentiaProviderMenuState provider)) return;

        var container = (WidgetContainerAccessor) widgets;
        var panel = container.tce$compositeWidgets().get("upgrades");
        if (panel == null && !container.tce$widgets().containsKey("upgrades")) {
            var slots = menu.getSlots(SlotSemantics.UPGRADE);
            if (slots.isEmpty()) return;
            panel = new UpgradesPanel(slots, () -> tce$providerUpgradeLines(menu));
            widgets.add("upgrades", panel);
        }
        tce$bindProviderTooltip(panel, provider);
    }

    @Unique
    private static List<Component> tce$providerUpgradeLines(AEBaseMenu menu) {
        if (!(menu instanceof PatternProviderMenu)) return List.of();
        var lines = new ArrayList<Component>();
        lines.add(GuiText.CompatibleUpgrades.text());
        if (menu.getTarget() instanceof PatternProviderLogicHost host) {
            lines.addAll(Upgrades.getTooltipLinesForMachine(host.getTerminalIcon().getItem()));
        }
        return lines;
    }

    @Inject(method = "updateBeforeRender()V", at = @At("TAIL"))
    private void tce$refreshProviderTooltip(CallbackInfo ci) {
        if (((AEBaseScreen<?>) (Object) this).getMenu() instanceof EssentiaProviderMenuState provider) {
            tce$bindProviderTooltip(((WidgetContainerAccessor) widgets).tce$compositeWidgets().get("upgrades"), provider);
        }
    }

    @Unique
    private void tce$bindProviderTooltip(ICompositeWidget panel, EssentiaProviderMenuState provider) {
        if (panel != tce$boundUpgradePanel && panel instanceof EssentiaUpgradeTooltip tooltip) {
            tooltip.filterEssentiaUpgrade(provider::essentiaSupportEnabled);
            tce$boundUpgradePanel = panel;
        }
    }
}
