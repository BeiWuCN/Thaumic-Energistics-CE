package thaumicenergistics_ce.mixin;

import appeng.api.behaviors.StackExportStrategy;
import appeng.api.behaviors.StackTransferContext;
import appeng.api.stacks.AEKey;
import appeng.parts.automation.ExportBusPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import thaumicenergistics_ce.busaccess.EssentiaBusAccess;
import thaumicenergistics_ce.integration.ae2.AEssentiaKey;

@Mixin(value = ExportBusPart.class, remap = false)
public abstract class ExportBusOperationMixin {
    @Redirect(method = {"doBusWork", "attemptCrafting"}, at = @At(value = "INVOKE",
            target = "Lappeng/api/stacks/AEKey;getAmountPerOperation()I"))
    private int tce$busTransferFactor(AEKey key) {
        return key instanceof AEssentiaKey ? EssentiaBusAccess.AMOUNT_PER_OPERATION : key.getAmountPerOperation();
    }

    @Redirect(method = "doBusWork", at = @At(value = "INVOKE",
            target = "Lappeng/api/behaviors/StackExportStrategy;transfer(Lappeng/api/behaviors/StackTransferContext;Lappeng/api/stacks/AEKey;J)J"))
    private long tce$roundPartialOperation(StackExportStrategy strategy, StackTransferContext context,
            AEKey key, long amount) {
        long transferred = strategy.transfer(context, key, amount);
        if (key instanceof AEssentiaKey && transferred >= EssentiaBusAccess.AMOUNT_PER_OPERATION
                && transferred % EssentiaBusAccess.AMOUNT_PER_OPERATION != 0) {
            context.reduceOperationsRemaining(1);
        }
        return transferred;
    }
}
