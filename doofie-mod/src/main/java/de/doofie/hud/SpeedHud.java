package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class SpeedHud {

    private static final int PAD     = 4;
    private static final int OFFSET_Y = 14;
    private static final int BG      = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        Vec3d vel = mc.player.getVelocity();
        double hSpeed = Math.sqrt(vel.x * vel.x + vel.z * vel.z) * 20.0;

        String label = String.format("Speed: %.2f b/s", hSpeed);
        int tw = mc.textRenderer.getWidth(label);
        int y = PAD + OFFSET_Y;

        ctx.fill(PAD, y, PAD + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), PAD + 3, y + 2, 0xFFCCFF, false);
    }
}
