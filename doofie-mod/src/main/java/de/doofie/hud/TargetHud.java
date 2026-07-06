package de.doofie.hud;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;

public class TargetHud {

    private static final int PAD = 4;
    private static final int BG  = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null || mc.world == null) return;

        HitResult hit = mc.crosshairTarget;
        if (hit == null || hit.getType() == HitResult.Type.MISS) return;

        String line1, line2 = null;

        if (hit.getType() == HitResult.Type.ENTITY) {
            Entity entity = ((EntityHitResult) hit).getEntity();
            line1 = "Ziel: " + entity.getName().getString();
            String type = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
            line2 = type;
        } else {
            BlockPos bpos = ((BlockHitResult) hit).getBlockPos();
            BlockState state = mc.world.getBlockState(bpos);
            String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
            line1 = "Block: " + state.getBlock().getName().getString();
            line2 = String.format("%d, %d, %d  (%s)", bpos.getX(), bpos.getY(), bpos.getZ(), blockId);
        }

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        int y  = sh / 2 + 12; // Just below crosshair center

        int tw1 = mc.textRenderer.getWidth(line1);
        ctx.fill(sw / 2 - tw1 / 2 - 3, y - 2, sw / 2 + tw1 / 2 + 3, y + 10, BG);
        ctx.drawText(mc.textRenderer, Text.literal(line1), sw / 2 - tw1 / 2, y, 0xFFFFFFFF, false);

        if (line2 != null) {
            int tw2 = mc.textRenderer.getWidth(line2);
            ctx.fill(sw / 2 - tw2 / 2 - 3, y + 11, sw / 2 + tw2 / 2 + 3, y + 23, BG);
            ctx.drawText(mc.textRenderer, Text.literal(line2), sw / 2 - tw2 / 2, y + 12, 0xFFAAAAAA, false);
        }
    }
}
