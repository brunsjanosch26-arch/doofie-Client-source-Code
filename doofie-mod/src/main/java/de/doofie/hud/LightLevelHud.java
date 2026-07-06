package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

public class LightLevelHud {

    private static final int PAD = 4;
    private static final int BG  = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null || mc.world == null) return;

        BlockPos pos   = mc.player.getBlockPos();
        int blockLight = mc.world.getLightLevel(LightType.BLOCK, pos);
        int skyLight   = mc.world.getLightLevel(LightType.SKY, pos);
        int combined   = Math.max(blockLight, skyLight);

        // Mobs spawn at combined light ≤ 0 in 1.18+; warn if block light is low
        int color = blockLight >= 8 ? 0xFF4ADE80 : blockLight >= 1 ? 0xFFFFAA00 : 0xFFFF5555;

        String label = String.format("Licht: %d  (B:%d S:%d)", combined, blockLight, skyLight);
        int tw = mc.textRenderer.getWidth(label);
        int y  = PAD + 70;

        ctx.fill(PAD, y, PAD + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), PAD + 3, y + 2, color, false);
    }
}
