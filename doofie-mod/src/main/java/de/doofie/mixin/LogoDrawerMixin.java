package de.doofie.mixin;

import de.doofie.DoofieTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.LogoDrawer;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ersetzt das Minecraft-Logo im Hauptmenue komplett durch den
 * grossen "DOOFIE CLIENT"-Schriftzug.
 */
@Mixin(LogoDrawer.class)
public class LogoDrawerMixin {

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IF)V", at = @At("HEAD"), cancellable = true)
    private void doofie$replaceLogo(DrawContext ctx, int screenWidth, float alpha, CallbackInfo ci) {
        ci.cancel();
        doofie$drawLogo(ctx, screenWidth, 30);
    }

    @Inject(method = "draw(Lnet/minecraft/client/gui/DrawContext;IFI)V", at = @At("HEAD"), cancellable = true)
    private void doofie$replaceLogoY(DrawContext ctx, int screenWidth, float alpha, int y, CallbackInfo ci) {
        ci.cancel();
        doofie$drawLogo(ctx, screenWidth, y);
    }

    private static void doofie$drawLogo(DrawContext ctx, int screenWidth, int y) {
        var tr = MinecraftClient.getInstance().textRenderer;

        Text logo = Text.literal("DOOFIE").formatted(Formatting.GOLD, Formatting.BOLD);
        Text sub = Text.literal("CLIENT").formatted(Formatting.WHITE, Formatting.BOLD);

        // Grosser Schriftzug: 4x skaliert, Schatten-Ebene fuer Tiefe
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(screenWidth / 2f, y);
        ctx.getMatrices().scale(4f, 4f);
        int w = tr.getWidth(logo);
        ctx.drawText(tr, logo, -w / 2, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();

        // "CLIENT" darunter, 2x skaliert
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate(screenWidth / 2f, y + 38);
        ctx.getMatrices().scale(2f, 2f);
        int w2 = tr.getWidth(sub);
        ctx.drawText(tr, sub, -w2 / 2, 0, 0xFFFFFFFF, true);
        ctx.getMatrices().popMatrix();
    }
}
