package de.doofie.hud;

import de.doofie.DoofieTheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Deque;

public class KeystrokesHud {

    private static final int KEY_SIZE = 22;
    private static final int KEY_GAP  = 2;
    private static final int PADDING  = 10;

    // ARGB colors
    private static final int BG_NORMAL  = 0xAA111111;
    private static final int BG_PRESSED = DoofieTheme.ACCENT;
    private static final int TEXT_NORMAL  = 0xFFAAAAAA;
    private static final int TEXT_PRESSED = 0xFFFFFFFF;
    private static final int BORDER_N = 0x55FFFFFF;
    private static final int BORDER_P = 0xFFFFFFFF;

    private static final Deque<Long> lmbTimes = new ArrayDeque<>();
    private static final Deque<Long> rmbTimes = new ArrayDeque<>();
    private static boolean prevLmb = false;
    private static boolean prevRmb = false;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        boolean w     = mc.options.forwardKey.isPressed();
        boolean a     = mc.options.leftKey.isPressed();
        boolean s     = mc.options.backKey.isPressed();
        boolean d     = mc.options.rightKey.isPressed();
        boolean space = mc.options.jumpKey.isPressed();
        boolean lmb   = mc.options.attackKey.isPressed();
        boolean rmb   = mc.options.useKey.isPressed();

        long now = System.currentTimeMillis();
        if (lmb && !prevLmb) lmbTimes.addLast(now);
        if (rmb && !prevRmb) rmbTimes.addLast(now);
        prevLmb = lmb;
        prevRmb = rmb;
        while (!lmbTimes.isEmpty() && now - lmbTimes.peekFirst() > 1000) lmbTimes.pollFirst();
        while (!rmbTimes.isEmpty() && now - rmbTimes.peekFirst() > 1000) rmbTimes.pollFirst();

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int totalW = KEY_SIZE * 3 + KEY_GAP * 2;
        int totalH = KEY_SIZE * 4 + KEY_GAP * 3 + 10;

        int baseX = sw - totalW - PADDING;
        int baseY = sh - totalH - PADDING - 30;

        // Row 1: W
        drawKey(ctx, mc, baseX + KEY_SIZE + KEY_GAP, baseY, KEY_SIZE, KEY_SIZE, "W", w);

        // Row 2: A S D
        int r2 = baseY + KEY_SIZE + KEY_GAP;
        drawKey(ctx, mc, baseX,                        r2, KEY_SIZE, KEY_SIZE, "A", a);
        drawKey(ctx, mc, baseX + KEY_SIZE + KEY_GAP,   r2, KEY_SIZE, KEY_SIZE, "S", s);
        drawKey(ctx, mc, baseX + (KEY_SIZE+KEY_GAP)*2, r2, KEY_SIZE, KEY_SIZE, "D", d);

        // Row 3: SPACE (full width)
        int r3 = r2 + KEY_SIZE + KEY_GAP;
        drawKey(ctx, mc, baseX, r3, totalW, KEY_SIZE, "SPC", space);

        // Row 4: LMB | RMB
        int r4 = r3 + KEY_SIZE + KEY_GAP;
        int half = (totalW - KEY_GAP) / 2;
        int lCps = lmbTimes.size();
        int rCps = rmbTimes.size();
        drawKey(ctx, mc, baseX,                  r4, half, KEY_SIZE, "LMB " + lCps, lmb);
        drawKey(ctx, mc, baseX + half + KEY_GAP, r4, half, KEY_SIZE, "RMB " + rCps, rmb);
    }

    private static void drawKey(DrawContext ctx, MinecraftClient mc,
                                int x, int y, int w, int h,
                                String label, boolean pressed) {
        ctx.fill(x, y, x + w, y + h, pressed ? BG_PRESSED : BG_NORMAL);

        int border = pressed ? BORDER_P : BORDER_N;
        ctx.fill(x,         y,     x + w,     y + 1,     border);
        ctx.fill(x,         y+h-1, x + w,     y + h,     border);
        ctx.fill(x,         y,     x + 1,     y + h,     border);
        ctx.fill(x + w - 1, y,     x + w,     y + h,     border);

        int textColor = pressed ? TEXT_PRESSED : TEXT_NORMAL;
        int tw = mc.textRenderer.getWidth(label);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 7) / 2;
        ctx.drawText(mc.textRenderer, Text.literal(label), tx, ty, textColor, true);
    }
}
