package thaumicenergistics_ce.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaPatternProvider;
import thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaReturnGate;

@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.logic.provider.OverloadedPatternProviderLogic", remap = false)
public abstract class LightningProviderEssentiaMixin implements EssentiaPatternProvider {
    @Shadow public abstract PatternProviderReturnInventory getInternalReturnInv();
    @Shadow public abstract PatternProviderReturnInventory getReturnInv();

    @Override
    public GenericStackInv essentiaBuffer() { return getInternalReturnInv(); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void tce$fullBuffer(CallbackInfo ci) {
        for (var inventory : java.util.List.of(getInternalReturnInv(), getReturnInv())) {
            var gate = (EssentiaReturnGate) inventory;
            gate.setEssentiaGate(this::essentiaEnabled);
            gate.setEssentiaChangeListener(() ->
                    ((thaumicenergistics_ce.integration.ae2.patternprovider.EssentiaProviderReturn)
                            essentiaReturnStorage()).inventoryChanged());
        }
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean tce$refuses(KeyCounter[] inputs) {
        if (inputs == null || essentiaEnabled()) return false;
        for (var input : inputs) for (var entry : input) {
            if (entry.getKey() instanceof AEssentiaKey) return true;
        }
        return false;
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void tce$gate(IPatternDetails details, KeyCounter[] inputs, CallbackInfoReturnable<Boolean> cir) {
        if (tce$refuses(inputs)) cir.setReturnValue(false);
    }

    @Inject(method = "pushBatch", at = @At("HEAD"), cancellable = true)
    private void tce$batchGate(IPatternDetails details, KeyCounter[] inputs, long maxCraft,
            CallbackInfoReturnable<Long> cir) {
        if (tce$refuses(inputs)) cir.setReturnValue(Math.max(0, maxCraft));
    }
}
