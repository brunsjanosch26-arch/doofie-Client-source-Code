package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class HealthHud {

    private static final int PAD    = 4;
    private static final int BG     = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        float health    = mc.player.getHealth();
        float maxHealth = mc.player.getMaxHealth();
        float pct       = health / maxHealth;
        int   color     = pct > 0.5f ? 0xFF4ADE80 : pct > 0.25f ? 0xFFFFAA00 : 0xFFFF5555;

        int sh = mc.getWindow().getScaledHeight();
        String label = String.format("❤ %.1f / %.0f", health, maxHealth);
        int tw = mc.textRenderer.getWidth(label);

        int x = PAD;
        int y = sh - PAD - 54; // Just above coordinates

        ctx.fill(x, y, x + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), x + 3, y + 2, color, false);
    }
}
