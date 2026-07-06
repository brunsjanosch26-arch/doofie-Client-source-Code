package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class CompassHud {

    private static final int HEIGHT = 16;
    private static final int W      = 200;
    private static final int BG     = 0x88000000;

    // Cardinal + intercardinal marks shown on a 360° compass bar
    private static final String[] DIRS  = {"S", "SW", "W", "NW", "N", "NE", "E", "SE", "S"};
    private static final int[]    DEGS  = {0,  45,   90, 135,  180, 225, 270, 315, 360};

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        int sw = mc.getWindow().getScaledWidth();
        int cx = sw / 2;
        int y  = 4;

        // Yaw: 0 = South, +90 = West, +180/-180 = North, -90 = East
        float yaw = ((mc.player.getYaw() % 360) + 360) % 360;

        ctx.fill(cx - W / 2, y, cx + W / 2, y + HEIGHT, BG);

        // Draw a center tick
        ctx.fill(cx - 1, y + HEIGHT - 4, cx + 1, y + HEIGHT, 0xFFFFFFFF);

        // Draw cardinal directions on the bar
        for (int i = 0; i < DIRS.length; i++) {
            float dirDeg = DEGS[i];
            // Angular distance (on circle) from player yaw
            float delta = dirDeg - yaw;
            // Wrap to [-180, 180]
            while (delta > 180)  delta -= 360;
            while (delta < -180) delta += 360;

            // Each degree = W/120 pixels (so ±60° fits in the bar)
            float pixelOffset = delta * (W / 120f);
            if (Math.abs(pixelOffset) > W / 2f) continue;

            int tx = cx + (int) pixelOffset;
            String label = DIRS[i];
            int lw = mc.textRenderer.getWidth(label);
            if (tx - lw / 2 < cx - W / 2 || tx + lw / 2 > cx + W / 2) continue;

            boolean isCardinal = label.length() == 1;
            int color = isCardinal ? 0xFFFFDD55 : 0xFFAAAAAA;
            ctx.drawText(mc.textRenderer, Text.literal(label), tx - lw / 2, y + 4, color, false);
        }

        // Current direction label above bar
        String curDir = getDirectionLabel(yaw);
        int dw = mc.textRenderer.getWidth(curDir);
        ctx.drawText(mc.textRenderer, Text.literal("§e" + curDir), cx - dw / 2, y - 10, 0xFFFFFFFF, false);
    }

    private static String getDirectionLabel(float yaw) {
        if (yaw < 22.5  || yaw >= 337.5) return "S";
        if (yaw < 67.5)                  return "SW";
        if (yaw < 112.5)                 return "W";
        if (yaw < 157.5)                 return "NW";
        if (yaw < 202.5)                 return "N";
        if (yaw < 247.5)                 return "NE";
        if (yaw < 292.5)                 return "E";
        return "SE";
    }
}
