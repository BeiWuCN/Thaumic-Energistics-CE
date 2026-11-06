package com.beiwu.thaumicenergistics_ce.mixin;

import com.beiwu.thaumicenergistics_ce.item.ThaumicTools;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve naming and enchantment merging while forbidding anvil durability repairs. */
@Mixin(AnvilMenu.class)
public abstract class ThaumicToolAnvilRepairMixin {
    @Redirect(method="createResult", at=@At(value="INVOKE", target="Lnet/minecraft/world/item/ItemStack;setDamageValue(I)V"))
    private void tce$keepDamage(ItemStack output, int damage) {
        ItemStack input=((AnvilMenu)(Object)this).getSlot(0).getItem();
        output.setDamageValue(input.getItem() instanceof ThaumicTools.Repairable
                ? Math.max(damage,input.getDamageValue()) : damage);
    }
}
