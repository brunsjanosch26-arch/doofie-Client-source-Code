package de.doofie;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;

/**
 * Doofie-Kampfeffekte: goldener Hitmarker beim Treffer,
 * roter Vignetten-Flash beim Kill. (26.2-Edition, Mojang-Mappings)
 */
public final class DoofieCombatFx {

    private static long hitTime = 0;
    private static long killTime = 0;
    private static LivingEntity lastTarget = null;

    private DoofieCombatFx() {}

    public static void onAttack(LivingEntity target) {
        hitTime = System.currentTimeMillis();
        lastTarget = target;
    }

    public static void tick() {
        if (lastTarget == null) return;
        // Ziel innerhalb 1s nach dem Treffer gestorben -> Kill-Flash
        if (System.currentTimeMillis() - hitTime < 1000 && (lastTarget.isDeadOrDying() || lastTarget.isRemoved())) {
            killTime = System.currentTimeMillis();
            lastTarget = null;
        } else if (System.currentTimeMillis() - hitTime >= 1000) {
            lastTarget = null;
        }
    }

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        // Hitmarker: 4 goldene Striche um das Fadenkreuz, 250ms fade
        long dtHit = now - hitTime;
        if (dtHit < 250) {
            int alpha = (int) (255 * (1 - dtHit / 250f));
            int color = (alpha << 24) | 0xFFC940;
            int cx = sw / 2, cy = sh / 2;
            int in = 3, out = 8;
            ctx.fill(cx - out, cy - out, cx - in, cy - out + 1, color);
            ctx.fill(cx - out, cy - out, cx - out + 1, cy - in, color);
            ctx.fill(cx + in, cy - out, cx + out, cy - out + 1, color);
            ctx.fill(cx + out - 1, cy - out, cx + out, cy - in, color);
            ctx.fill(cx - out, cy + out - 1, cx - in, cy + out, color);
            ctx.fill(cx - out, cy + in, cx - out + 1, cy + out, color);
            ctx.fill(cx + in, cy + out - 1, cx + out, cy + out, color);
            ctx.fill(cx + out - 1, cy + in, cx + out, cy + out, color);
        }

        // Kill-Flash: rote Vignette, 400ms fade
        long dtKill = now - killTime;
        if (killTime > 0 && dtKill < 400) {
            int alpha = (int) (70 * (1 - dtKill / 400f));
            int color = (alpha << 24) | 0xBE1E28;
            int border = Math.min(sw, sh) / 6;
            ctx.fill(0, 0, sw, border, color);
            ctx.fill(0, sh - border, sw, sh, color);
            ctx.fill(0, border, border, sh - border, color);
            ctx.fill(sw - border, border, sw, sh - border, color);
        }
    }
}
