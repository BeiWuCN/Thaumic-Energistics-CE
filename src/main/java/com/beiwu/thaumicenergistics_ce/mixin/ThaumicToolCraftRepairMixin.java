package com.beiwu.thaumicenergistics_ce.mixin;

import com.beiwu.thaumicenergistics_ce.item.ThaumicTools;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RepairItemRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 1.21.1 crafting repair does not consult Item.isRepairable. */
@Mixin(RepairItemRecipe.class)
public abstract class ThaumicToolCraftRepairMixin {
    @Inject(method="canCombine", at=@At("HEAD"), cancellable=true)
    private static void tce$pedestalOnly(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        if (first.getItem() instanceof ThaumicTools.Repairable || second.getItem() instanceof ThaumicTools.Repairable) {
            cir.setReturnValue(false);
        }
    }
}
