package de.doofie.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Mod-Verwaltung fuer 26.2-Profile. Statt eigener Maus-Logik (deren Signaturen
 * sich zwischen Versionen aendern) besteht die Liste aus normalen Buttons —
 * das ist gegen Mapping-/API-Wechsel robust.
 */
public class DoofieModScreen extends Screen {

    // Auto-installierte Mods der 26.2-Profile (Marker-Dateiname: <slug>-bsmp-26.2.jar)
    private static final List<ModEntry> ALL_MODS = List.of(
        new ModEntry("AppleSkin",            "appleskin-bsmp-26.2.jar"),
        new ModEntry("Cloth Config",         "cloth-config-bsmp-26.2.jar"),
        new ModEntry("Dynamic FPS",          "dynamic-fps-bsmp-26.2.jar"),
        new ModEntry("Entity Culling",       "entityculling-bsmp-26.2.jar"),
        new ModEntry("Fabric Kotlin",        "fabric-language-kotlin-bsmp-26.2.jar"),
        new ModEntry("FerriteCore",          "ferritecore-bsmp-26.2.jar"),
        new ModEntry("ImmediatelyFast",      "immediatelyfast-bsmp-26.2.jar"),
        new ModEntry("Iris Shaders",         "iris-bsmp-26.2.jar"),
        new ModEntry("Lithium",              "lithium-bsmp-26.2.jar"),
        new ModEntry("Mod Menu",             "modmenu-bsmp-26.2.jar"),
        new ModEntry("More Culling",         "moreculling-bsmp-26.2.jar"),
        new ModEntry("Mouse Tweaks",         "mouse-tweaks-bsmp-26.2.jar"),
        new ModEntry("Skin Layers 3D",       "3dskinlayers-bsmp-26.2.jar"),
        new ModEntry("Sodium",               "sodium-bsmp-26.2.jar"),
        new ModEntry("Status Effect Bars",   "status-effect-bars-bsmp-26.2.jar"),
        new ModEntry("Voice Chat",           "simple-voice-chat-bsmp-26.2.jar"),
        new ModEntry("YACL",                 "yacl-bsmp-26.2.jar"),
        new ModEntry("Zoomify",              "zoomify-bsmp-26.2.jar")
    );

    private final Screen parent;
    private final Map<String, Boolean> enabled = new LinkedHashMap<>();
    private int scrollOffset = 0;
    private boolean saved = false;

    private static final int ROW_H = 24;
    private static final int LIST_TOP = 52;

    public DoofieModScreen(Screen parent) {
        super(Component.literal("Doofie Client"));
        this.parent = parent;
        for (ModEntry e : ALL_MODS) enabled.put(e.file(), true);
        loadConfig();
    }

    private int visRows() {
        return (height - LIST_TOP - 38) / ROW_H;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.literal("← Zurück"), b -> onClose())
            .bounds(8, height - 26, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("✓ Speichern"), b -> {
            saveConfig();
            saved = true;
        }).bounds(width / 2 - 55, height - 26, 110, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Alle AN"), b -> {
            enabled.replaceAll((k, v) -> true);
            rebuildWidgets();
        }).bounds(width - 82, height - 26, 74, 20).build());

        // Scroll-Buttons
        addRenderableWidget(Button.builder(Component.literal("▲"), b -> {
            scrollOffset = Math.max(0, scrollOffset - 1);
            rebuildWidgets();
        }).bounds(width - 24, LIST_TOP, 16, 20).build());
        addRenderableWidget(Button.builder(Component.literal("▼"), b -> {
            scrollOffset = Math.min(Math.max(0, ALL_MODS.size() - visRows()), scrollOffset + 1);
            rebuildWidgets();
        }).bounds(width - 24, LIST_TOP + 24, 16, 20).build());

        // Mod-Zeilen als Toggle-Buttons
        for (int i = 0; i < visRows(); i++) {
            int idx = i + scrollOffset;
            if (idx >= ALL_MODS.size()) break;
            ModEntry entry = ALL_MODS.get(idx);
            int ry = LIST_TOP + i * ROW_H;
            boolean on = enabled.getOrDefault(entry.file(), true);
            addRenderableWidget(Button.builder(
                Component.literal((on ? "§a✔ " : "§c✘ ") + entry.name()),
                b -> {
                    enabled.put(entry.file(), !enabled.getOrDefault(entry.file(), true));
                    rebuildWidgets();
                })
                .bounds(10, ry + 1, width - 44, ROW_H - 2).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
        super.extractRenderState(ctx, mx, my, delta);

        // Header
        ctx.fill(0, 0, width, 46, 0xD008111F);
        ctx.fill(0, 45, width, 46, 0xFF3D7FF0);
        ctx.centeredText(font, Component.literal("§b§lDoofie Client §r§7| Mods"), width / 2, 8, 0xFFFFFF);
        ctx.centeredText(font, Component.literal("§7Wähle welche Mods aktiv sein sollen · Neustart erforderlich"), width / 2, 24, 0xAAAAAA);

        // Footer
        ctx.fill(0, height - 32, width, height, 0xD008111F);
        ctx.fill(0, height - 33, width, height - 32, 0xFF3D7FF0);
        if (saved) {
            ctx.centeredText(font,
                Component.literal("§e⚠ Gespeichert — Neustart erforderlich"), width / 2, height - 45, 0xFFFF55);
        }
    }

    @Override
    public void onClose() {
        assert minecraft != null;
        minecraft.setScreenAndShow(parent);
    }

    // Config at gameDir/doofieclient_mods.json (gleiches Format wie 1.21.11-Edition)

    private Path cfgPath() {
        assert minecraft != null;
        return minecraft.gameDirectory.toPath().resolve("doofieclient_mods.json");
    }

    private void loadConfig() {
        Path p = cfgPath();
        if (!Files.exists(p)) return;
        try {
            String raw = Files.readString(p).trim();
            int s = raw.indexOf('['), e = raw.lastIndexOf(']');
            if (s >= 0 && e > s) {
                Set<String> active = new HashSet<>();
                for (String part : raw.substring(s + 1, e).split(",")) {
                    active.add(part.trim().replace("\"", ""));
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
