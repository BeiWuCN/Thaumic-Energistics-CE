package com.beiwu.thaumicenergistics_ce.mixin;

import appeng.parts.reporting.AbstractMonitorPart;
import appeng.parts.reporting.StorageMonitorPart;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import com.leclowndu93150.thaumaturge.api.essentia.EssentiaCapabilities;
import com.leclowndu93150.thaumaturge.registry.TTDataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.beiwu.thaumicenergistics_ce.integration.ae2.AEssentiaKey;

/** Use the ordinary ME Storage Monitor and its native watchers, persistence and display. */
@Mixin(value = AbstractMonitorPart.class, remap = false)
public abstract class StorageMonitorEssentiaMixin {
    @Shadow protected abstract void configureWatchers();

    @Inject(method = "onUseItemOn", at = @At("HEAD"), cancellable = true)
    private void tce$selectEssentia(ItemStack heldItem, Player player, InteractionHand hand, Vec3 pos,
            CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof StorageMonitorPart monitor)
                || InteractionUtil.isInAlternateUseMode(player)) return;
        boolean wrench = InteractionUtil.canWrenchRotate(heldItem);
        var filter = heldItem.get(TTDataComponents.ASPECT_FILTER.get());
        AEssentiaKey key = filter == null ? null : AEssentiaKey.of(filter.identifier());
        if (key == null) {
            var container = heldItem.getCapability(EssentiaCapabilities.CONTAINER);
            var aspects = container == null ? null : container.getAspects();
            if (aspects != null && aspects.entries().size() == 1) {
                key = AEssentiaKey.of(aspects.entries().getFirst().aspect());
            }
        }
        if (!wrench && (key == null || monitor.isLocked())) return;
        if (player.level().isClientSide()) {
            cir.setReturnValue(true);
            return;
        }
        if (!monitor.getMainNode().isActive() || !Platform.hasPermissions(monitor.getHost().getLocation(), player)) {
            cir.setReturnValue(false);
            return;
        }
        if (wrench) {
            monitor.setLocked(!monitor.isLocked());
        } else {
            monitor.setConfiguredItem(key);
            configureWatchers();
        }
        monitor.getHost().markForSave();
        monitor.getHost().markForUpdate();
        cir.setReturnValue(true);
    }

    @Inject(method = "onUseWithoutItem", at = @At("HEAD"), cancellable = true)
    private void tce$clearEssentia(Player player, Vec3 pos, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof StorageMonitorPart monitor)
                || monitor.isLocked() || InteractionUtil.isInAlternateUseMode(player)
                || !(monitor.getDisplayed() instanceof AEssentiaKey)) return;
        if (player.level().isClientSide()) {
            cir.setReturnValue(true);
            return;
        }
        if (!monitor.getMainNode().isActive() || !Platform.hasPermissions(monitor.getHost().getLocation(), player)) {
            cir.setReturnValue(false);
            return;
        }
        monitor.setConfiguredItem(null);
        configureWatchers();
        // AE2's configureWatchers skips its reading refresh for a null key.
        tce$resetReading(monitor);
        monitor.getHost().markForSave();
        monitor.getHost().markForUpdate();
        cir.setReturnValue(true);
    }

    @Shadow protected abstract void updateReportingValue(appeng.api.networking.IGrid grid);

    @Unique
    private void tce$resetReading(StorageMonitorPart monitor) {
        monitor.getMainNode().ifPresent(this::updateReportingValue);
    }
}
