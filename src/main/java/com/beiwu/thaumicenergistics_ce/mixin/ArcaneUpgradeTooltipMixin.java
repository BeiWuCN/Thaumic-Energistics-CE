package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.Upgrades;
import appeng.client.gui.widgets.UpgradesPanel;
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
import com.beiwu.thaumicenergistics_ce.registry.TECEItems;

@Mixin(value = UpgradesPanel.class, remap = false)
public abstract class ArcaneUpgradeTooltipMixin implements com.beiwu.thaumicenergistics_ce.client.gui.EssentiaUpgradeTooltip {
    @Shadow @Final @Mutable private Supplier<List<Component>> tooltipSupplier;

    @org.spongepowered.asm.mixin.Unique private Supplier<List<Component>> tce$originalTooltip;
    @org.spongepowered.asm.mixin.Unique private java.util.function.BooleanSupplier tce$essentiaEnabled;

    @Override
    public void filterEssentiaUpgrade(java.util.function.BooleanSupplier enabled) {
        tce$essentiaEnabled = enabled;
        if (tce$originalTooltip != null) return;
        tce$originalTooltip = tooltipSupplier;
        tooltipSupplier = () -> com.beiwu.thaumicenergistics_ce.integration.ae2.patternprovider.ProviderUpgradeTooltip.apply(
                tce$originalTooltip.get(), tce$essentiaEnabled.getAsBoolean());
    }

    @Inject(method = "<init>(Ljava/util/List;Lappeng/api/upgrades/IUpgradeableObject;)V", at = @At("RETURN"))
    private void filterArcaneUpgrades(List<Slot> slots, IUpgradeableObject host, CallbackInfo ci) {
        var machine = host.getUpgrades().getUpgradableItem().asItem();
        // 四个奥术终端都要同一套标题结构。编码终端以前漏在这里，结果是它的 tooltip
        // 只剩一行卡名，而合成终端有「可用升级：」开头，同一个 mod 两种长相。
        boolean ours = machine == TECEItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()
                || machine == TECEItems.ARCANE_CRAFTING_TERMINAL.get()
                || machine == TECEItems.WIRELESS_ARCANE_ENCODING_TERMINAL.get()
                || machine == TECEItems.ARCANE_ENCODING_TERMINAL.get()
                || machine == TECEItems.WIRELESS_ESSENTIA_TERMINAL.get();
        if (!ours) return;
        tooltipSupplier = () -> {
            var lines = new ArrayList<>(Upgrades.getTooltipLinesForMachine(machine));
            if (!lines.isEmpty()) {
                lines.addFirst(Component.translatable("gui.ae2.CompatibleUpgrades"));
            }
            return lines;
        };
    }
}
