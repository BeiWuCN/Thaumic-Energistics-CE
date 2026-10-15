package thaumicenergistics_ce.mixin;

import appeng.api.stacks.AEKeyType;
import appeng.client.gui.me.common.MEStorageScreen;
import appeng.client.gui.me.common.Repo;
import java.util.HashSet;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import thaumicenergistics_ce.integration.ae2.AEssentiaKeyType;
import thaumicenergistics_ce.menu.EssentiaVisibility;

@Mixin(value = MEStorageScreen.class, remap = false)
public abstract class TerminalScreenVisibilityMixin {
    @Shadow @Final protected Repo repo;
    @Unique private Boolean thaumicenergistics$lastVisibility;

    @org.spongepowered.asm.mixin.injection.ModifyArg(
            method = "<init>",
            at = @At(value = "INVOKE", target = "Lappeng/client/gui/widgets/UpgradesPanel;<init>(Ljava/util/List;Lappeng/api/upgrades/IUpgradeableObject;)V"),
            index = 0)
    private java.util.List<net.minecraft.world.inventory.Slot> joinQuantumSlot(
            java.util.List<net.minecraft.world.inventory.Slot> upgrades) {
        var menu = ((MEStorageScreen<?>) (Object) this).getMenu();
        if (!(menu instanceof thaumicenergistics_ce.menu.MenuEssentiaTerminalBase terminal)) {
            return upgrades;
        }
        var singularity = terminal.getSlots(thaumicenergistics_ce.menu.MenuEssentiaTerminalBase.QUANTUM_SINGULARITY);
        if (singularity.isEmpty()) return upgrades;
        var combined = new java.util.ArrayList<net.minecraft.world.inventory.Slot>(upgrades);
        combined.addAll(singularity);
        return combined;
    }

    @Inject(method = "getSortKeyTypes", at = @At("RETURN"), cancellable = true)
    private void filterTypes(CallbackInfoReturnable<Set<AEKeyType>> cir) {
        var screen = (MEStorageScreen<?>) (Object) this;
        if (!EssentiaVisibility.allowed(screen.getMenu())) {
            Set<AEKeyType> types = new HashSet<>(cir.getReturnValue());
            types.remove(AEssentiaKeyType.INSTANCE);
            cir.setReturnValue(types);
        }
    }

    @Inject(method = "updateBeforeRender", at = @At("HEAD"))
    private void refreshVisibility(CallbackInfo ci) {
        boolean visible = EssentiaVisibility.allowed(((MEStorageScreen<?>) (Object) this).getMenu());
        if (thaumicenergistics$lastVisibility == null || thaumicenergistics$lastVisibility != visible) {
            thaumicenergistics$lastVisibility = visible;
            boolean paused = repo.isPaused();
            if (paused) {
                repo.setPaused(false);
            } else {
                repo.updateView();
            }
            if (paused) {
                repo.setPaused(true);
            }
        }
    }
}
