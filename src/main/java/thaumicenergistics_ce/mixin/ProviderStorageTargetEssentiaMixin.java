package thaumicenergistics_ce.mixin;

import appeng.api.networking.security.IActionSource;
import appeng.helpers.patternprovider.PatternProviderTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.integration.ae2.PatternProviderAddonCompat;
import thaumicenergistics_ce.integration.ae2.EssentiaProviderTarget;

@Mixin(value = PatternProviderTarget.class, remap = false)
public interface ProviderStorageTargetEssentiaMixin {
    @Inject(method = "get", at = @At("RETURN"), cancellable = true)
    private static void tce$sourceGate(Level level, BlockPos pos, BlockEntity be, Direction side,
            IActionSource source, CallbackInfoReturnable<PatternProviderTarget> cir) {
        var machine = source.machine().orElse(null);
        if (machine == null) return;
        var provider = PatternProviderAddonCompat.provider(machine);
        if (provider == null && machine.getActionableNode() != null) {
            provider = PatternProviderAddonCompat.provider(machine.getActionableNode().getOwner());
        }
        if (provider != null) {
            cir.setReturnValue(new EssentiaProviderTarget(cir.getReturnValue(), level, pos,
                    side, source, provider::essentiaEnabled));
        }
    }
}
