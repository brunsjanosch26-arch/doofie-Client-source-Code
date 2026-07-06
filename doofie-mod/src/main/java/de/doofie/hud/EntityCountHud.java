package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.text.Text;

public class EntityCountHud {

    private static final int PAD    = 4;
    private static final int BG     = 0x88000000;
    private static final double RADIUS = 64.0;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null || mc.world == null) return;

        int hostile = 0, passive = 0, players = 0;
        for (Entity e : mc.world.getEntities()) {
            if (e == mc.player) continue;
            if (e.distanceTo(mc.player) > RADIUS) continue;
            if (e instanceof HostileEntity) hostile++;
            else if (e instanceof AnimalEntity) passive++;
            else if (e instanceof net.minecraft.entity.player.PlayerEntity) players++;
        }

        String label = String.format("Entities (64m): §c%d Mob  §a%d Tier  §b%d Spieler§r", hostile, passive, players);
        int tw = mc.textRenderer.getWidth(label);
        int y  = PAD + 112;

        ctx.fill(PAD, y, PAD + tw + 6, y + 12, BG);
        ctx.drawText(mc.textRenderer, Text.literal(label), PAD + 3, y + 2, 0xFFFFFFFF, false);
    }
}
