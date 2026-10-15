package thaumicenergistics_ce.mixin;

import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics_ce.integration.ae2.EssentiaBusHost;

@Mixin(value = AEBasePart.class, remap = false)
public abstract class BusRecoveryItemMixin {
    @Inject(method = "exportSettings(Lappeng/util/SettingsFrom;Lnet/minecraft/core/component/DataComponentMap$Builder;)V",
            at = @At("TAIL"))
    private void tce$save(SettingsFrom mode, DataComponentMap.Builder builder, CallbackInfo ci) {
        if (mode == SettingsFrom.DISMANTLE_ITEM && (Object) this instanceof EssentiaBusHost host
                && !host.tce$recoveryBuffer().isEmpty()) {
            var part = (AEBasePart) (Object) this;
            var previous = builder.build().get(DataComponents.CUSTOM_DATA);
            var tag = previous == null ? new CompoundTag() : previous.copyTag();
            host.tce$recoveryBuffer().write(tag, part.getLevel().registryAccess());
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    @Inject(method = "importSettings", at = @At("TAIL"))
    private void tce$load(SettingsFrom mode, DataComponentMap input, Player player, CallbackInfo ci) {
        if (mode == SettingsFrom.DISMANTLE_ITEM && (Object) this instanceof EssentiaBusHost host) {
            var data = input.get(DataComponents.CUSTOM_DATA);
            if (data != null) host.tce$recoveryBuffer().read(data.copyTag(), player.registryAccess());
        }
    }
}
