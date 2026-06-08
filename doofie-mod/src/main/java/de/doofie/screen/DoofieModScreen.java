package de.doofie.screen;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DoofieModScreen extends Screen {

    // Bekannte Client-Mods (Anzeigename + Dateiname)
    private static final List<ModEntry> ALL_MODS = List.of(
        new ModEntry("AppleSkin",           "appleskin-fabric-mc1.21.11-3.0.8.jar"),
        new ModEntry("Armor HUD",           "Armor_Hud-3.2-1.21.11.jar"),
        new ModEntry("Dynamic FPS",         "dynamic-fps-3.11.6+minecraft-1.21.11-fabric.jar"),
        new ModEntry("Entity Culling",      "entityculling-fabric-1.10.2-mc1.21.11.jar"),
        new ModEntry("FerriteCore",         "ferritecore-8.2.0-fabric.jar"),
        new ModEntry("ImmediatelyFast",     "ImmediatelyFast-Fabric-1.14.2+1.21.11.jar"),
        new ModEntry("Iris Shaders",        "iris-fabric-1.10.7+mc1.21.11.jar"),
        new ModEntry("Item Scale",          "itemscale-1.21.X.jar"),
        new ModEntry("Jade",                "Jade-1.21.11-Fabric-21.1.6.jar"),
        new ModEntry("JEI",                 "jei-1.21.11-fabric-27.4.0.22.jar"),
        new ModEntry("Key Overlay",         "KeyOverlay-1.21.5+.jar"),
        new ModEntry("Lithium",             "lithium-fabric-0.21.4+mc1.21.11.jar"),
        new ModEntry("Mod Menu",            "modmenu-17.0.0.jar"),
        new ModEntry("More Culling",        "moreculling-fabric-1.21.11-1.6.2.jar"),
        new ModEntry("Mouse Tweaks",        "MouseTweaks-fabric-mc1.21.11-2.30.jar"),
        new ModEntry("Shulker Tooltip",     "shulkerboxtooltip-fabric-5.2.16+1.21.11.jar"),
        new ModEntry("Simple Block Overlay","simpleblockoverlay-1.6.4+1.21.11-fabric.jar"),
        new ModEntry("Skin Layers 3D",      "skinlayers3d-fabric-1.11.1-mc1.21.11.jar"),
        new ModEntry("Sodium",              "sodium-fabric-0.8.12+mc1.21.11.jar"),
        new ModEntry("Status Effect Bars",  "status-effect-bars-1.0.10.jar"),
        new ModEntry("Visuality",           "visuality-0.7.13+1.21.11.jar"),
        new ModEntry("Voice Chat",          "voicechat-fabric-1.21.11-2.6.18.jar")
    );

    private final Screen parent;
    private final Map<String, Boolean> enabled = new LinkedHashMap<>();
    private int scrollOffset = 0;
    private boolean saved = false;

    private static final int ROW_H   = 24;
    private static final int LIST_TOP = 52;

    public DoofieModScreen(Screen parent) {
        super(Text.literal("Doofie Client"));
        this.parent = parent;
        for (ModEntry e : ALL_MODS) enabled.put(e.file(), true);
        loadConfig();
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("← Zurück"),  b -> close())
            .dimensions(8, height - 26, 80, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("✓ Speichern"), b -> {
            saveConfig(); saved = true;
        }).dimensions(width / 2 - 55, height - 26, 110, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("Alle AN"), b ->
            enabled.replaceAll((k, v) -> true)
        ).dimensions(width - 82, height - 26, 74, 20).build());
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx, mx, my, delta);

        // Header
        ctx.fill(0, 0, width, 46, 0xD008111F);
        ctx.fill(0, 45, width, 46, 0xFF3D7FF0);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§b§lDoofie Client §r§7| Mods"), width / 2, 8, 0xFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Wähle welche Mods aktiv sein sollen · Neustart erforderlich"), width / 2, 24, 0xAAAAAA);

        // Footer
        ctx.fill(0, height - 32, width, height, 0xD008111F);
        ctx.fill(0, height - 33, width, height - 32, 0xFF3D7FF0);
        if (saved) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§e⚠ Gespeichert — Neustart erforderlich"), width / 2, height - 45, 0xFFFF55);
        }

        // Mod list
        int listH = height - LIST_TOP - 38;
        int visRows = listH / ROW_H;
        ctx.enableScissor(0, LIST_TOP, width, LIST_TOP + listH);

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= ALL_MODS.size()) break;
            ModEntry entry = ALL_MODS.get(idx);
            int ry = LIST_TOP + i * ROW_H;
            boolean on = enabled.getOrDefault(entry.file(), true);

            ctx.fill(0, ry, width, ry + ROW_H, i % 2 == 0 ? 0x22FFFFFF : 0x11FFFFFF);

            // Status bar
            ctx.fill(0, ry + 2, 4, ry + ROW_H - 2, on ? 0xFF4ADE80 : 0xFF666666);

            ctx.drawText(textRenderer, Text.literal(entry.name()), 12, ry + 4, on ? 0xE0E0E0 : 0x888888, false);
            ctx.drawText(textRenderer, Text.literal("§8" + entry.file()), 12, ry + 14, 0x555555, false);

            // Toggle button
            int tx = width - 52;
            boolean hov = mx >= tx && mx <= width-8 && my >= ry+3 && my <= ry+ROW_H-3;
            ctx.fill(tx, ry+3, width-8, ry+ROW_H-3, hov ? 0x44FFFFFF : 0x22FFFFFF);
            ctx.drawText(textRenderer, Text.literal(on ? "§aAN" : "§cAUS"), tx+4, ry+8, 0xFFFFFF, false);
        }

        ctx.disableScissor();

        // Scrollbar
        if (ALL_MODS.size() > visRows) {
            int trackH = listH;
            int thumbH = Math.max(16, trackH * visRows / ALL_MODS.size());
            int maxScroll = Math.max(1, ALL_MODS.size() - visRows);
            int thumbY = LIST_TOP + (trackH - thumbH) * scrollOffset / maxScroll;
            ctx.fill(width - 4, LIST_TOP, width, LIST_TOP + listH, 0x33FFFFFF);
            ctx.fill(width - 4, thumbY, width, thumbY + thumbH, 0xAA3D7FF0);
        }

        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmt, double vAmt) {
        int listH = height - LIST_TOP - 38;
        int visRows = listH / ROW_H;
        int maxScroll = Math.max(0, ALL_MODS.size() - visRows);
        scrollOffset = (int) Math.max(0, Math.min(maxScroll, scrollOffset - vAmt * 2));
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        double mx = click.x();
        double my = click.y();

        int listH = height - LIST_TOP - 38;
        int visRows = listH / ROW_H;

        for (int i = 0; i < visRows; i++) {
            int idx = i + scrollOffset;
            if (idx >= ALL_MODS.size()) break;
            String file = ALL_MODS.get(idx).file();
            int ry = LIST_TOP + i * ROW_H;

            if (my >= ry && my < ry + ROW_H) {
                boolean wasOn = enabled.getOrDefault(file, true);
                enabled.put(file, !wasOn);
                return true;
            }
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public void close() {
        assert client != null;
        client.setScreen(parent);
    }

    // Config at gameDir/doofieclient_mods.json

    private Path cfgPath() {
        assert client != null;
        return client.runDirectory.toPath().resolve("doofieclient_mods.json");
    }

    private void loadConfig() {
        Path p = cfgPath();
        if (!Files.exists(p)) return;
        try {
            String raw = Files.readString(p).trim();
            int s = raw.indexOf('['), e = raw.lastIndexOf(']');
            if (s >= 0 && e > s) {
                Set<String> active = new HashSet<>();
                for (String part : raw.substring(s+1, e).split(",")) {
                    active.add(part.trim().replace("\"",""));
                }
                enabled.replaceAll((k, v) -> active.contains(k));
            }
        } catch (IOException ignored) {}
    }

    private void saveConfig() {
        StringBuilder sb = new StringBuilder("{\"enabled\":[");
        boolean first = true;
        for (Map.Entry<String, Boolean> e : enabled.entrySet()) {
            if (!e.getValue()) continue;
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\"");
            first = false;
        }
        sb.append("]}");
        try { Files.writeString(cfgPath(), sb); } catch (IOException ignored) {}
    }

    record ModEntry(String name, String file) {}
}
