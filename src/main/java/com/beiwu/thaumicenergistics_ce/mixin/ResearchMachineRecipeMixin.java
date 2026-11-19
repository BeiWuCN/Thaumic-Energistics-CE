package com.beiwu.thaumicenergistics_ce.mixin;

import com.beiwu.thaumicenergistics_ce.client.ThaumicMachineRecipes;
import com.leclowndu93150.thaumaturge.client.render.research.RecipeDisplayWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value=RecipeDisplayWidget.class,remap=false)
public abstract class ResearchMachineRecipeMixin {
    @Inject(method="renderCrafting(Lnet/minecraft/client/gui/GuiGraphics;IILnet/minecraft/world/item/crafting/RecipeHolder;JFI)V",
            at=@At("HEAD"),cancellable=true)
    private static void tce$machine(GuiGraphics graphics,int x,int y,RecipeHolder<?> display,
            long gameTime,float rotation,int layer,CallbackInfo ci) {
        if(ThaumicMachineRecipes.supports(display)) {
            ThaumicMachineRecipes.render(graphics,x,y,display);
            ci.cancel();
        }
    }

    @Inject(method="hoverItemForDisplay",at=@At("HEAD"),cancellable=true)
    private static void tce$hover(int x,int y,RecipeHolder<?> display,double mouseX,double mouseY,
            CallbackInfoReturnable<RecipeDisplayWidget.ItemHit> cir) {
        if(!ThaumicMachineRecipes.supports(display))return;
        cir.setReturnValue(ThaumicMachineRecipes.hits(x,y,display).stream()
                .filter(hit->hit.contains(mouseX,mouseY)).findFirst().orElse(null));
    }
}
