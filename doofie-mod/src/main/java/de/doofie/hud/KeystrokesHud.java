package de.doofie.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class KeystrokesHud {

    private static final int KEY_SIZE  = 20;
    private static final int KEY_GAP   = 3;
    private static final int PADDING   = 12;

    private static final int BG_NORMAL  = 0x88000000;
    private static final int BG_PRESSED = 0xCC3D7FF0;
    private static final int BORDER_N   = 0x663D7FF0;
    private static final int BORDER_P   = 0xFF7FBFFF;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return;
        if (mc.player == null) return;

        long win = mc.getWindow().getHandle();
        boolean w     = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_W)     == GLFW.GLFW_PRESS;
        boolean a     = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_A)     == GLFW.GLFW_PRESS;
        boolean s     = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_S)     == GLFW.GLFW_PRESS;
        boolean d     = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_D)     == GLFW.GLFW_PRESS;
        boolean space = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_SPACE) == GLFW.GLFW_PRESS;
        boolean lmb   = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT)  == GLFW.GLFW_PRESS;
        boolean rmb   = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();

        int totalW = KEY_SIZE * 3 + KEY_GAP * 2;
        int totalH = KEY_SIZE * 4 + KEY_GAP * 3;

        int baseX = sw - totalW - PADDING;
        int baseY = sh - totalH - PADDING - 30;

        // Row 1: W centered
        drawKey(ctx, mc, baseX + KEY_SIZE + KEY_GAP, baseY, KEY_SIZE, KEY_SIZE, "W", w);

        // Row 2: A S D
        int r2 = baseY + KEY_SIZE + KEY_GAP;
        drawKey(ctx, mc, baseX,                         r2, KEY_SIZE, KEY_SIZE, "A", a);
        drawKey(ctx, mc, baseX + KEY_SIZE + KEY_GAP,    r2, KEY_SIZE, KEY_SIZE, "S", s);
        drawKey(ctx, mc, baseX + (KEY_SIZE+KEY_GAP)*2,  r2, KEY_SIZE, KEY_SIZE, "D", d);

        // Row 3: SPACE
        int spaceW = totalW;
        int r3 = r2 + KEY_SIZE + KEY_GAP;
        drawKey(ctx, mc, baseX, r3, spaceW, KEY_SIZE, "SPC", space);

        // Row 4: LMB | RMB
        int r4 = r3 + KEY_SIZE + KEY_GAP;
        int half = (spaceW - KEY_GAP) / 2;
        drawKey(ctx, mc, baseX,             r4, half, KEY_SIZE, "LMB", lmb);
        drawKey(ctx, mc, baseX + half + KEY_GAP, r4, half, KEY_SIZE, "RMB", rmb);
    }

    private static void drawKey(DrawContext ctx, MinecraftClient mc,
                                  int x, int y, int w, int h,
                                  String label, boolean pressed) {
        int bg     = pressed ? BG_PRESSED : BG_NORMAL;
        int border = pressed ? BORDER_P   : BORDER_N;

        ctx.fill(x, y, x + w, y + h, bg);
        ctx.fill(x,         y,     x + w, y + 1,     border);
        ctx.fill(x,         y+h-1, x + w, y + h,     border);
        ctx.fill(x,         y,     x + 1, y + h,     border);
        ctx.fill(x + w - 1, y,     x + w, y + h,     border);

        if (pressed) {
            ctx.fill(x-1, y-1, x+w+1, y,     0x443D7FF0);
            ctx.fill(x-1, y+h, x+w+1, y+h+1, 0x443D7FF0);
            ctx.fill(x-1, y,   x,     y+h,   0x443D7FF0);
            ctx.fill(x+w, y,   x+w+1, y+h,   0x443D7FF0);
        }

        int textColor = pressed ? 0xFFFFFF : 0xCCCCCC;
        int tw = mc.textRenderer.getWidth(label);
        int tx = x + (w - tw) / 2;
        int ty = y + (h - 7) / 2;
        ctx.drawText(mc.textRenderer, Text.literal(label), tx, ty, textColor, false);
    }
}
