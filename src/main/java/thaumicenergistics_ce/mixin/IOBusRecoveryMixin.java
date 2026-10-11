package thaumicenergistics_ce.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.parts.automation.IOBusPart;
import appeng.parts.automation.UpgradeablePart;
import appeng.api.parts.IPartItem;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.busaccess.EssentiaBusBuffer;
import thaumicenergistics_ce.busaccess.EssentiaBusHost;
import thaumicenergistics_ce.busaccess.EssentiaBusAccess;

@Mixin(value = IOBusPart.class, remap = false)
public abstract class IOBusRecoveryMixin extends UpgradeablePart implements EssentiaBusHost {
    protected IOBusRecoveryMixin(IPartItem<?> item) {
        super(item);
    }
    @Unique
    private EssentiaBusBuffer tce$recovery;

    @Shadow
    protected abstract int getOperationsPerTick();

    @Override
    public EssentiaBusBuffer tce$recoveryBuffer() {
        if (tce$recovery == null) {
            tce$recovery = new EssentiaBusBuffer(() -> {
                var part = (IOBusPart) (Object) this;
                if (part.getHost() != null) {
                    part.getHost().markForSave();
                }
                part.getMainNode().ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
            });
        }
        return tce$recovery;
    }

    @Override
    public void clearContent() {
        super.clearContent();
        if (tce$recovery != null) {
            tce$recovery.clear();
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void tce$readRecovery(ValueInput input, CallbackInfo callback) {
        tce$recoveryBuffer().read(input);
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void tce$writeRecovery(ValueOutput output, CallbackInfo callback) {
        if (tce$recovery != null) {
            tce$recovery.write(output);
        }
    }

    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true)
    private void tce$returnRecovery(IGridNode node, int elapsed,
            CallbackInfoReturnable<TickRateModulation> callback) {
        var part = (IOBusPart) (Object) this;
        if (tce$recovery != null && !tce$recovery.isEmpty() && part.getMainNode().isActive()) {
            long returned = tce$recovery.returnToNetwork(node.getGrid().getStorageService().getInventory(),
                    IActionSource.ofMachine(part),
                    (long) getOperationsPerTick() * EssentiaBusAccess.AMOUNT_PER_OPERATION,
                    node.getGrid().getEnergyService());
            callback.setReturnValue(returned > 0 ? TickRateModulation.FASTER : TickRateModulation.SLOWER);
        }
    }

    @Inject(method = "tickingRequest", at = @At("RETURN"), cancellable = true)
    private void tce$retryRecovery(IGridNode node, int elapsed,
            CallbackInfoReturnable<TickRateModulation> callback) {
        if (tce$recovery != null && !tce$recovery.isEmpty()
                && callback.getReturnValue() == TickRateModulation.SLEEP) {
            callback.setReturnValue(TickRateModulation.SLOWER);
        }
    }

    @Inject(method = "getTickingRequest", at = @At("RETURN"), cancellable = true)
    private void tce$wakeLoadedRecovery(IGridNode node, CallbackInfoReturnable<TickingRequest> callback) {
        if (tce$recovery != null && !tce$recovery.isEmpty()) {
            var original = callback.getReturnValue();
            callback.setReturnValue(new TickingRequest(original.minTickRate(), original.maxTickRate(),
                    false, original.initialTickRate()));
        }
    }
}
