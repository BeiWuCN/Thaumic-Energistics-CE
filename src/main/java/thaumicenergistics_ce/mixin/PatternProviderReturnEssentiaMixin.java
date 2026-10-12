package thaumicenergistics_ce.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaReturnGate;

@Mixin(value = PatternProviderReturnInventory.class, remap = false)
public abstract class PatternProviderReturnEssentiaMixin {
    @Inject(method = "injectIntoNetwork", at = @At("RETURN"))
    private void tce$networkReturn(MEStorage storage, IActionSource source, Consumer<GenericStack> callback,
            CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) ((EssentiaReturnGate) this).notifyEssentiaChange();
    }
}
