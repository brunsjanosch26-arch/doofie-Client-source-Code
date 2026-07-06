package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class MemoryHud {

    private static final int PAD = 4;
    private static final int BG  = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        Runtime rt = Runtime.getRuntime();
        long usedMB  = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long totalMB = rt.maxMemory() / (1024 * 1024);
        float pct    = (float) usedMB / totalMB;

        int color = pct < 0.6f ? 0xFF4ADE80 : pct < 0.85f ? 0xFFFFAA00 : 0xFFFF5555;
        String label = String.format("RAM: %d / %d MB", usedMB, totalMB);
        int tw = mc.textRenderer.getWidth(label);

        int y = PAD + 56; // Below Zeit (PAD+42) with some gap

        ctx.fill(PAD, y, PAD + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), PAD + 3, y + 2, color, false);
    }
}
