package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

public class ChunkHud {

    private static final int PAD = 4;
    private static final int BG  = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        BlockPos pos = mc.player.getBlockPos();
        ChunkPos chunk = new ChunkPos(pos);
        // Local position within the chunk (0-15)
        int lx = pos.getX() & 15;
        int lz = pos.getZ() & 15;

        String label = String.format("Chunk: %d, %d  [%d, %d]", chunk.x, chunk.z, lx, lz);
        int tw = mc.textRenderer.getWidth(label);
        int y  = PAD + 98;

        ctx.fill(PAD, y, PAD + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), PAD + 3, y + 2, 0xFFAAAAAA, false);
    }
}
