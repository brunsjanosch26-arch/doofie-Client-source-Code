package de.doofie.hud;

import de.doofie.DoofieTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class StatusEffectsHud {

    private static final int PAD     = 4;
    private static final int LINE_H  = 11;
    private static final int BG      = 0x88000000;
    private static final int BG_GOOD = DoofieTheme.ACCENT_SOFT;
    private static final int BG_BAD  = 0x55FF4444;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        List<StatusEffectInstance> effects = new ArrayList<>(mc.player.getStatusEffects());
        if (effects.isEmpty()) return;

        // Sort: positive first, then by duration descending
        effects.sort(Comparator
            .comparing((StatusEffectInstance e) -> !e.getEffectType().value().isBeneficial())
            .thenComparingInt(e -> -e.getDuration()));

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int totalH = effects.size() * LINE_H + 4;
        int maxW   = 0;
        List<String> labels = new ArrayList<>();
        for (StatusEffectInstance e : effects) {
            String name = e.getEffectType().value().getName().getString();
            int lvl = e.getAmplifier();
            String lvlStr = lvl > 0 ? " " + toRoman(lvl + 1) : "";
            int dur = e.getDuration();
            String durStr = dur > 20 * 3600 ? "∞" : formatTicks(dur);
            String label = name + lvlStr + "  " + durStr;
            labels.add(label);
            maxW = Math.max(maxW, mc.textRenderer.getWidth(label));
        }

        // Position: below armor HUD (top-right, under armor)
        int bgX = sw - PAD - maxW - 6;
        int bgY = PAD + 50;

        ctx.fill(bgX, bgY, sw - PAD, bgY + totalH, BG);

        for (int i = 0; i < effects.size(); i++) {
            StatusEffectInstance e = effects.get(i);
            boolean good = e.getEffectType().value().isBeneficial();
            int rowY = bgY + 2 + i * LINE_H;
            ctx.fill(bgX, rowY, sw - PAD, rowY + LINE_H - 1, good ? BG_GOOD : BG_BAD);

            int pct255 = (int)(255 * Math.min(1f, e.getDuration() / (20f * 30)));
            int alpha = 0xFF - Math.min(0x88, 0xFF - pct255);
            int textColor = good
                ? (0xFF000000 | (alpha << 16) | (alpha) | 0x7FBFFF)
                : (0xFF000000 | (alpha << 16) | 0x5555);

            // Simple colored text
            int color = good ? 0xFF7FBFFF : 0xFFFF6666;
            if (e.getDuration() < 20 * 5) color = 0xFFFFAA00; // flashing low

            ctx.drawText(mc.textRenderer, Text.literal(labels.get(i)),
                bgX + 3, rowY + 2, color, false);
        }
    }

    private static String formatTicks(int ticks) {
        int sec = ticks / 20;
        if (sec < 60) return sec + "s";
        return (sec / 60) + "m" + (sec % 60 != 0 ? (sec % 60) + "s" : "");
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(n);
        };
    }
}
