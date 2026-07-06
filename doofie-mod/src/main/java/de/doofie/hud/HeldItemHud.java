package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class HeldItemHud {

    private static final int PAD = 4;
    private static final int BG  = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        ItemStack held = mc.player.getMainHandStack();
        if (held.isEmpty()) return;

        int sh = mc.getWindow().getScaledHeight();

        String name = held.getName().getString();
        String info;
        int color;

        int maxDur = held.getMaxDamage();
        if (maxDur > 0) {
            int cur = maxDur - held.getDamage();
            float pct = (float) cur / maxDur;
            color = pct > 0.5f ? 0xFF4ADE80 : pct > 0.25f ? 0xFFFFAA00 : 0xFFFF5555;
            info = name + "  " + cur + "/" + maxDur;
        } else if (held.getCount() > 1) {
            color = 0xFFCCCCCC;
            info = name + "  x" + held.getCount();
        } else {
            return; // Nothing interesting to show
        }

        int tw = mc.textRenderer.getWidth(info);
        int sw = mc.getWindow().getScaledWidth();
        int x  = sw / 2 - tw / 2;
        int y  = sh - 55;

        ctx.fill(x - 3, y - 2, x + tw + 3, y + 10, BG);
        ctx.drawText(mc.textRenderer, Text.literal(info), x, y, color, false);
    }
}
