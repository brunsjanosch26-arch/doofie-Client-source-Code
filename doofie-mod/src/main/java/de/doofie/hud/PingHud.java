package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.Text;

public class PingHud {

    private static final int PAD    = 4;
    private static final int BG     = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (mc.isIntegratedServerRunning()) return; // No ping in single-player

        PlayerListEntry entry = mc.getNetworkHandler()
            .getPlayerListEntry(mc.player.getUuid());
        if (entry == null) return;

        int ping = entry.getLatency();
        int color = ping < 50 ? 0xFF4ADE80
                  : ping < 100 ? 0xFF99FF66
                  : ping < 200 ? 0xFFFFAA00
                  : 0xFFFF5555;

        String label = "Ping: " + ping + "ms";
        int tw = mc.textRenderer.getWidth(label);
        int y  = PAD + 28; // Below speed

        ctx.fill(PAD, y, PAD + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), PAD + 3, y + 2, color, false);
    }
}
