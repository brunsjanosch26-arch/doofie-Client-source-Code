package de.doofie.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Doofie-Branding im Hauptmenue: Badge unten rechts + Tagline unter dem Logo.
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void doofie$renderBranding(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        var tr = MinecraftClient.getInstance().textRenderer;

        // Badge unten rechts: "D DOOFIE CLIENT" (deckt NRC-Branding ab)
        int x = this.width - 120;
        int y = this.height - 14;
        ctx.fill(x - 4, y - 3, this.width - 2, y + 12, 0xCC000000);
        Text badge = Text.literal("D ").formatted(Formatting.GOLD)
            .append(Text.literal("DOOFIE ").formatted(Formatting.WHITE))
            .append(Text.literal("CLIENT").formatted(Formatting.GOLD));
        ctx.drawText(tr, badge, x, y, 0xFFFFFF, true);
    }
}
