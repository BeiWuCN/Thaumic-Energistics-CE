package thaumicenergistics_ce.mixin;

import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics_ce.busaccess.EssentiaBusHost;

@Mixin(value = AEBasePart.class, remap = false)
public abstract class BusRecoveryItemMixin {
    @Inject(method = "exportSettings(Lappeng/util/SettingsFrom;Lnet/minecraft/core/component/DataComponentMap$Builder;)V",
            at = @At("TAIL"))
    private void tce$keepRecoveryInDrop(SettingsFrom mode, DataComponentMap.Builder builder, CallbackInfo callback) {
        if (mode == SettingsFrom.DISMANTLE_ITEM && (Object) this instanceof EssentiaBusHost host
                && !host.tce$recoveryBuffer().isEmpty()) {
            var part = (AEBasePart) (Object) this;
            var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, part.getLevel().registryAccess());
            host.tce$recoveryBuffer().write(output);
            builder.set(DataComponents.CUSTOM_DATA, CustomData.of(output.buildResult()));
        }
    }

    @Inject(method = "importSettings", at = @At("TAIL"))
    private void tce$restoreRecoveryFromItem(SettingsFrom mode, DataComponentMap input, Player player,
            CallbackInfo callback) {
        if (mode == SettingsFrom.DISMANTLE_ITEM && (Object) this instanceof EssentiaBusHost host) {
            var data = input.get(DataComponents.CUSTOM_DATA);
            if (data != null) {
                var part = (AEBasePart) (Object) this;
                host.tce$recoveryBuffer().read(TagValueInput.create(ProblemReporter.DISCARDING,
                        part.getLevel().registryAccess(), data.copyTag()));
            }
        }
    }
}
