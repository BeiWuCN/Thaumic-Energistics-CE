package thaumicenergistics_ce.mixin;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.widgets.UpgradesPanel;
import appeng.core.localization.GuiText;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics_ce.config.ThEConfig;
import thaumicenergistics_ce.registry.TECEItems;

@Mixin(value = UpgradesPanel.class, remap = false)
public abstract class ArcaneUpgradeTooltipMixin implements thaumicenergistics_ce.client.gui.EssentiaUpgradeTooltip {
    @Shadow @Final @Mutable private Supplier<List<Component>> tooltipSupplier;

    @Override
    public void filterEssentiaUpgrade(java.util.function.BooleanSupplier enabled) {
        var original = tooltipSupplier;
        tooltipSupplier = () -> thaumicenergistics_ce.integration.ae2.patternprovider.ProviderUpgradeTooltip.apply(
                original.get(), enabled.getAsBoolean());
    }

    @Inject(method = "<init>(Ljava/util/List;Lappeng/api/upgrades/IUpgradeableObject;)V", at = @At("RETURN"))
    private void filterArcaneUpgrades(List<Slot> slots, IUpgradeableObject host, CallbackInfo ci) {
        var machine = host.getUpgrades().getUpgradableItem().asItem();
        boolean arcane = machine == TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()
                || machine == TECEItems.ARCANE_CRAFTING_TERMINAL.get();
        if (!arcane && machine != TECEItems.WIRELESS_ESSENTIA_TERMINAL.get()) return;
        tooltipSupplier = () -> {
            var lines = new ArrayList<>(Upgrades.getTooltipLinesForMachine(machine));
            if (arcane && !ThEConfig.ARCANE_ESSENTIA_ACCESS.get()) {
                var hidden = GuiText.CompatibleUpgrade.text(
                        TECEItems.ESSENTIA_ACCESS_CARD.get().getDefaultInstance().getItemName(), 1).getString();
                lines.removeIf(line -> line.getString().equals(hidden));
            }
            if (!lines.isEmpty()) {
                lines.addFirst(Component.translatable("gui.ae2.CompatibleUpgrades"));
            }
            return lines;
        };
    }
}
