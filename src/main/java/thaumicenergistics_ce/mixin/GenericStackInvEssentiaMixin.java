package thaumicenergistics_ce.mixin;

import appeng.api.stacks.AEKey;
import appeng.helpers.externalstorage.GenericStackInv;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaReturnGate;

@Mixin(value = GenericStackInv.class, remap = false)
public abstract class GenericStackInvEssentiaMixin implements EssentiaReturnGate {
    @Unique private BooleanSupplier tce$essentiaGate;
    @Unique private Runnable tce$essentiaListener;

    @Override
    public void setEssentiaGate(BooleanSupplier enabled) { tce$essentiaGate = enabled; }

    @Override
    public void setEssentiaChangeListener(Runnable listener) { tce$essentiaListener = listener; }

    @Override
    public void notifyEssentiaChange() {
        if (tce$essentiaListener != null) tce$essentiaListener.run();
    }

    @Inject(method = "onChange", at = @At("TAIL"))
    private void tce$changed(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        notifyEssentiaChange();
    }

    @Inject(method = "isAllowedIn", at = @At("RETURN"), cancellable = true)
    private void tce$gate(int slot, AEKey what, CallbackInfoReturnable<Boolean> cir) {
        if (what instanceof AEssentiaKey && tce$essentiaGate != null && !tce$essentiaGate.getAsBoolean()) {
            cir.setReturnValue(false);
        }
    }
}
