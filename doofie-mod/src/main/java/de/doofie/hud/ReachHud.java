package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class ReachHud {

    private static final int PAD = 4;
    private static final int BG  = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;

        double dist = mc.player.getEyePos().distanceTo(hit.getPos());
        String target = hit.getType() == HitResult.Type.ENTITY ? "Entity" : "Block";
        String label  = String.format("Reach: %.2fm (%s)", dist, target);
        int color = 0xFFCCCCCC;
        int tw = mc.textRenderer.getWidth(label);
        int y  = PAD + 84;

        ctx.fill(PAD, y, PAD + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), PAD + 3, y + 2, color, false);
    }
}
