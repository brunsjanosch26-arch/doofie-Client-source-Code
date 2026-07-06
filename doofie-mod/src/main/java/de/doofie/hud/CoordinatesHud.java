package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.Optional;

public class CoordinatesHud {

    private static final int PAD    = 4;
    private static final int LINE_H = 10;
    private static final int BG     = 0x88000000;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null || mc.world == null) return;

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        float yaw = ((mc.player.getYaw() % 360) + 360) % 360;
        String dir;
        if      (yaw < 45 || yaw >= 315) dir = "S";
        else if (yaw < 135)              dir = "W";
        else if (yaw < 225)              dir = "N";
        else                             dir = "E";

        String dimension = mc.world.getRegistryKey().getValue().getPath()
                .replace("_", " ");
        dimension = capitalizeWords(dimension);

        BlockPos blockPos = mc.player.getBlockPos();
        String biome = "?";
        Optional<RegistryKey<Biome>> biomeKey = mc.world.getBiome(blockPos).getKey();
        if (biomeKey.isPresent()) {
            biome = capitalizeWords(biomeKey.get().getValue().getPath().replace("_", " "));
        }

        String line1 = String.format("XYZ: %.1f / %.1f / %.1f", px, py, pz);
        String line2 = "Dir: " + dir + "  |  Dim: " + dimension;
        String line3 = "Biome: " + biome;

        int sh = mc.getWindow().getScaledHeight();
        int maxW = Math.max(mc.textRenderer.getWidth(line1),
                   Math.max(mc.textRenderer.getWidth(line2), mc.textRenderer.getWidth(line3)));

        int bgY = sh - PAD - LINE_H * 3 - 4;
        ctx.fill(PAD, bgY, PAD + maxW + 6, sh - PAD, BG);
        ctx.drawText(mc.textRenderer, Text.literal(line1), PAD + 3, bgY + 2,              0xFFFFFF, false);
        ctx.drawText(mc.textRenderer, Text.literal(line2), PAD + 3, bgY + 2 + LINE_H,     0xCCCCCC, false);
        ctx.drawText(mc.textRenderer, Text.literal(line3), PAD + 3, bgY + 2 + LINE_H * 2, 0xAAAAAA, false);
    }

    private static String capitalizeWords(String str) {
        if (str.isEmpty()) return str;
        StringBuilder sb = new StringBuilder();
        for (String word : str.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) sb.append(word.substring(1));
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}
