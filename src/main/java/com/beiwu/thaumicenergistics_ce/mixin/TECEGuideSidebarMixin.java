package com.beiwu.thaumicenergistics_ce.mixin;

import com.beiwu.thaumicenergistics_ce.ThEIds;
import guideme.Guide;
import guideme.document.LytRect;
import guideme.internal.screen.DocumentScreen;
import guideme.internal.screen.GuideNavBar;
import guideme.internal.screen.GuideScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GuideScreen.class, remap = false)
public abstract class TECEGuideSidebarMixin extends DocumentScreen {
    private static final int MIN_READING_WIDTH = 260;

    @Shadow @Final private Guide guide;

    protected TECEGuideSidebarMixin(Component title) {
        super(title);
    }

    @Inject(method = "hasSpaceForSidebar", at = @At("HEAD"), cancellable = true)
    private void tece$allowDockedNavigation(CallbackInfoReturnable<Boolean> callback) {
        LytRect viewport = screenRect;
        if (guide.getId().equals(ThEIds.id("guide"))
                && viewport.width() >= GuideNavBar.WIDTH_OPEN + MIN_READING_WIDTH) {
            callback.setReturnValue(true);
        }
    }
}
