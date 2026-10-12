package thaumicenergistics_ce.mixin;

import net.minecraft.client.resources.language.I18n;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModConfigSpec.ValueSpec.class, remap = false)
public abstract class ConfigCommentI18nMixin {
    @Shadow public abstract String getTranslationKey();

    @Inject(method = "getComment", at = @At("RETURN"), cancellable = true)
    private void localizeComment(CallbackInfoReturnable<String> cir) {
        var key = getTranslationKey();
        if (key == null || !key.startsWith("thaumicenergistics_ce.config.") || !I18n.exists(key + ".tooltip")) return;
        cir.setReturnValue(I18n.get(key + ".tooltip"));
    }
}
