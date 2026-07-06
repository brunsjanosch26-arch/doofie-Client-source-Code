package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class FpsHud {

    private static final int PAD = 4;
    private static final int BG  = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        int fps = mc.getCurrentFps();
        String label = "FPS: " + fps;
        int color = fps >= 60 ? 0xFF4ADE80 : fps >= 30 ? 0xFFFFAA00 : 0xFFFF5555;

        int tw = mc.textRenderer.getWidth(label);
        ctx.fill(PAD, PAD, PAD + tw + 6, PAD + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), PAD + 3, PAD + 2, color, false);
    }
}
