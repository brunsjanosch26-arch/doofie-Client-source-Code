package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class TimeHud {

    private static final int PAD    = 4;
    private static final int BG     = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null || mc.world == null) return;

        // Minecraft time: 0 = 6:00 AM, 6000 = noon, 12000 = 6 PM, 18000 = midnight
        long rawTime  = mc.world.getTimeOfDay() % 24000;
        long gameMins = (rawTime * 1440) / 24000;
        int  gameH    = (int)(gameMins / 60);
        int  gameM    = (int)(gameMins % 60);
        long dayCount = mc.world.getTime() / 24000;

        String timeLine = String.format("Zeit: %02d:%02d  Tag %d", gameH, gameM, dayCount + 1);
        boolean isDay   = rawTime >= 0 && rawTime < 12000;
        int color = isDay ? 0xFFFFDD44 : 0xFF8899CC;

        int tw = mc.textRenderer.getWidth(timeLine);
        int y  = PAD + 42; // Below ping

        ctx.fill(PAD, y, PAD + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(timeLine), PAD + 3, y + 2, color, false);
    }
}
