package thaumicenergistics_ce.mixin;

import appeng.parts.automation.IOBusPart;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.automation.ExportBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import thaumicenergistics_ce.integration.ae2.bus.EssentiaBusAccess;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import net.minecraft.world.level.storage.ValueInput;

@Mixin(value = IOBusPart.class, remap = false)
public abstract class IOBusUpgradeSlotsMixin {
    @Unique
    private boolean tce$hadAccessCard;

    @Inject(method = "getUpgradeSlots", at = @At("HEAD"), cancellable = true)
    private void tce$accessCardSlot(CallbackInfoReturnable<Integer> callback) {
        Object self = this;
        if (self instanceof ImportBusPart || self instanceof ExportBusPart) {
            callback.setReturnValue(EssentiaBusAccess.SLOT_COUNT);
        }
    }

    @Inject(method = "upgradesChanged", at = @At("TAIL"))
    private void tce$enableNewImportType(CallbackInfo callback) {
        Object self = this;
        if (self instanceof ImportBusPart bus) {
            boolean hasCard = EssentiaBusAccess.hasCard(bus);
            if (hasCard && !tce$hadAccessCard) {
                bus.getKeyTypeSelection().setEnabled(AEssentiaKeyType.INSTANCE, true);
            }
            tce$hadAccessCard = hasCard;
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void tce$restoreInstalledCardState(ValueInput input, CallbackInfo callback) {
        tce$hadAccessCard = EssentiaBusAccess.hasCard(this);
    }
}
