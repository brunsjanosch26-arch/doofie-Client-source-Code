package de.doofie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cancels the NRC BrandingPauseScreenMixin render to avoid missing texture.
 * Our PauseScreenMixin provides the "D DOOFIE CLIENT" overlay instead.
 */
@Mixin(targets = "gg.norisk.client.v2.mixin.branding.BrandingPauseScreenMixin", remap = false)
public class NrcBrandingPauseScreenMixin {

    @Inject(method = "nrc$renderBranding", at = @At("HEAD"), cancellable = true, remap = false)
    private void doofie$cancelNrcBranding(CallbackInfo ci) {
        ci.cancel();
    }
}
