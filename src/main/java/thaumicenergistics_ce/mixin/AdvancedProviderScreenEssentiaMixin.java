package thaumicenergistics_ce.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import thaumicenergistics_ce.integration.ae2.EssentiaProviderMenuState;

@Pseudo
@Mixin(targets = {"net.pedroksl.advanced_ae.client.gui.AdvPatternProviderScreen",
        "net.pedroksl.advanced_ae.client.gui.SmallAdvPatternProviderScreen",
        "com.moakiee.ae2lt.client.provider.PigmeePatternProviderScreen"}, priority = 500, remap = false)
public abstract class AdvancedProviderScreenEssentiaMixin extends AEBaseScreen<AEBaseMenu> {
    protected AdvancedProviderScreenEssentiaMixin(AEBaseMenu menu, Inventory inv, Component title, ScreenStyle style) {
        super(menu, inv, title, style);
    }
    @Override
    protected void init() {
        var panels = ((WidgetContainerAccessor) widgets).tce$compositeWidgets();
        if (!panels.containsKey("upgrades")) {
            widgets.add("upgrades", new UpgradesPanel(menu.getSlots(SlotSemantics.UPGRADE), List::of));
        }
        if (panels.get("upgrades") instanceof thaumicenergistics_ce.client.gui.EssentiaUpgradeTooltip tooltip) {
            tooltip.filterEssentiaUpgrade(() -> ((EssentiaProviderMenuState) menu).essentiaSupportEnabled());
        }
        super.init();
    }
}
