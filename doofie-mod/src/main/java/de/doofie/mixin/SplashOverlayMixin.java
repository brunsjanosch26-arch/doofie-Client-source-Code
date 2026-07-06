package de.doofie.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.IntSupplier;

/**
 * Erzwingt den roten Doofie-Ladebildschirm beim Spielstart.
 * NRC faerbt BRAND_ARGB dunkelblau um — wir setzen bei jedem Frame
 * wieder Rot, damit Doofie immer gewinnt.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow @Final @Mutable
    private static IntSupplier BRAND_ARGB;

    // Doofie-Rot (klassisches Mojang-Rot)
    private static final int DOOFIE_RED = ColorHelper.getArgb(255, 190, 30, 40);

    @Inject(method = "render", at = @At("HEAD"))
    private void doofie$forceRedSplash(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        BRAND_ARGB = () -> DOOFIE_RED;
    }
}
