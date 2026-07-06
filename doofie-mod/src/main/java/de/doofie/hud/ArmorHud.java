package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

public class ArmorHud {

    private static final int PAD    = 4;
    private static final int LINE_H = 10;
    private static final int BG     = 0x88000000;
    private static final int WIDTH  = 82;

    private static final EquipmentSlot[] SLOTS  = {
        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final String[] LABELS = { "Helm", "Brust", "Beine", "Boots" };

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        boolean hasArmor = false;
        for (EquipmentSlot slot : SLOTS) {
            if (!mc.player.getEquippedStack(slot).isEmpty()) { hasArmor = true; break; }
        }
        if (!hasArmor) return;

        int sw = mc.getWindow().getScaledWidth();
        int bgX = sw - PAD - WIDTH;
        int bgY = PAD;

        ctx.fill(bgX - 3, bgY, sw - PAD, bgY + LINE_H * SLOTS.length + 4, BG);

        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = mc.player.getEquippedStack(SLOTS[i]);
            int ry = bgY + 2 + i * LINE_H;

            if (stack.isEmpty()) {
                ctx.drawText(mc.textRenderer,
                    Text.literal(LABELS[i] + ": ---"), bgX, ry, 0x555555, false);
                continue;
            }

            int maxDur = stack.getMaxDamage();
            if (maxDur <= 0) {
                ctx.drawText(mc.textRenderer,
                    Text.literal(LABELS[i] + ": ∞"), bgX, ry, 0xAAAAAA, false);
                continue;
            }

            int cur = maxDur - stack.getDamage();
            float pct = (float) cur / maxDur;
            int color = pct > 0.5f ? 0xFF4ADE80 : pct > 0.25f ? 0xFFFFAA00 : 0xFFFF5555;

            String label = String.format("%s: %d/%d", LABELS[i], cur, maxDur);
            ctx.drawText(mc.textRenderer, Text.literal(label), bgX, ry, color, false);
        }
    }
}
