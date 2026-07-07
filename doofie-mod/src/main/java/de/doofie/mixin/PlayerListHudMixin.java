package de.doofie.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.PlayerListHud;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * "D DOOFIE CLIENT"-Header ueber der Tab-Spielerliste.
 */
@Mixin(PlayerListHud.class)
public class PlayerListHudMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void doofie$renderTabBranding(DrawContext ctx, int scaledWindowWidth, Scoreboard scoreboard, ScoreboardObjective objective, CallbackInfo ci) {
        var tr = MinecraftClient.getInstance().textRenderer;
        Text badge = Text.literal("D ").formatted(Formatting.GOLD)
            .append(Text.literal("DOOFIE ").formatted(Formatting.WHITE))
            .append(Text.literal("CLIENT").formatted(Formatting.GOLD));
        int w = tr.getWidth(badge);
        ctx.fill(scaledWindowWidth / 2 - w / 2 - 4, 0, scaledWindowWidth / 2 + w / 2 + 4, 12, 0xAA000000);
        ctx.drawText(tr, badge, scaledWindowWidth / 2 - w / 2, 2, 0xFFFFFF, true);
    }
}
