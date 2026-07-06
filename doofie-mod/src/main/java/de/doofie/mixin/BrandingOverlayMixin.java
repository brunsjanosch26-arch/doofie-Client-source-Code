package de.doofie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the NRC BrandingOverlay HUD element entirely.
 */
@Mixin(targets = "gg.norisk.client.v2.modules.impl.BrandingOverlay", remap = false)
public class BrandingOverlayMixin {

    @Inject(method = "isEnabled", at = @At("HEAD"), cancellable = true, remap = false)
    private void doofie$disableBranding(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(false);
    }
}
