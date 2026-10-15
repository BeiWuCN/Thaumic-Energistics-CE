package thaumicenergistics_ce.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PatternProviderScreen.class, priority = 500, remap = false)
public abstract class PatternProviderScreenEssentiaMixin extends AEBaseScreen<PatternProviderMenu> {
    protected PatternProviderScreenEssentiaMixin(PatternProviderMenu menu, Inventory inv, Component title, ScreenStyle style) {
        super(menu, inv, title, style);
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tce$panel(PatternProviderMenu menu, Inventory inv, Component title, ScreenStyle style, CallbackInfo ci) {
        if (!((WidgetContainerAccessor) widgets).tce$compositeWidgets().containsKey("upgrades")) {
        widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), () -> {
            var lines = new java.util.ArrayList<Component>();
            lines.add(appeng.core.localization.GuiText.CompatibleUpgrades.text());
            if (menu.getTarget() instanceof appeng.helpers.patternprovider.PatternProviderLogicHost host) {
                lines.addAll(appeng.api.upgrades.Upgrades.getTooltipLinesForMachine(
                        host.getTerminalIcon().getItem()));
            }
            return lines;
        }));
        }
        tce$bindTooltip();
    }

    @org.spongepowered.asm.mixin.Unique
    private Object tce$filteredPanel;

    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void tce$refreshTooltip(CallbackInfo ci) { tce$bindTooltip(); }

    @org.spongepowered.asm.mixin.Unique
    private void tce$bindTooltip() {
        var panel = ((WidgetContainerAccessor) widgets).tce$compositeWidgets().get("upgrades");
        if (panel != tce$filteredPanel
                && panel instanceof thaumicenergistics_ce.client.gui.EssentiaUpgradeTooltip tooltip) {
            tooltip.filterEssentiaUpgrade(() ->
                    ((thaumicenergistics_ce.integration.ae2.EssentiaProviderMenuState)
                            menu).essentiaSupportEnabled());
            tce$filteredPanel = panel;
        }
    }
}
