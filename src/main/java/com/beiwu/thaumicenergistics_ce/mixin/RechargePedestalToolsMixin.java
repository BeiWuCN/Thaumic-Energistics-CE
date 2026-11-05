package com.beiwu.thaumicenergistics_ce.mixin;

import com.beiwu.thaumicenergistics_ce.item.ThaumicTools;
import com.beiwu.thaumicenergistics_ce.item.VisToolRepair;
import com.leclowndu93150.thaumaturge.api.items.RechargeAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extend the existing pedestal, including its normal item sync and sparkle effects. */
@Mixin(targets = "com.leclowndu93150.thaumaturge.content.aura.BlockEntityRechargePedestal")
public abstract class RechargePedestalToolsMixin {
    @Inject(method = "accepts", at = @At("HEAD"), cancellable = true)
    private static void tce$acceptTools(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof ThaumicTools.Repairable) cir.setReturnValue(true);
    }

    @Redirect(method = "tickServer", at = @At(value = "INVOKE", target =
            "Lcom/leclowndu93150/thaumaturge/api/items/RechargeAccess;rechargeItem(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/entity/player/Player;I)F"))
    private float tce$repairOnPedestal(Level level, ItemStack stack, BlockPos pos, Player player, int amount) {
        if (stack.getItem() instanceof ThaumicTools.Repairable) {
            return level instanceof ServerLevel server && VisToolRepair.repair(stack, server, pos) ? 1 : 0;
        }
        return RechargeAccess.rechargeItem(level, stack, pos, player, amount);
    }
}
