package de.doofie;

/**
 * Zentrales Design-Token-System der Doofie-Mod.
 * Alle HUD-Elemente nutzen diese Farben, damit das Branding ueberall einheitlich ist.
 */
public final class DoofieTheme {

    private DoofieTheme() {}

    /** Halbtransparenter Standard-Hintergrund aller HUD-Boxen. */
    public static final int BG = 0x88000000;

    /**
     * Doofie-Akzent — Standard Gold, kann vom Launcher via
     * JVM-Property {@code -Ddoofie.accent=RRGGBB} auf die
     * Launcher-Akzentfarbe gesetzt werden (Theme-Handshake).
     */
    public static final int ACCENT;

    /** Doofie-Akzent halbtransparent — z.B. positive Statuseffekte. */
    public static final int ACCENT_SOFT;

    static {
        int rgb = 0xFFC940;
        String prop = System.getProperty("doofie.accent");
        if (prop != null) {
            try {
                rgb = Integer.parseInt(prop.replace("#", ""), 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {}
        }
        ACCENT = 0xDD000000 | rgb;
        ACCENT_SOFT = 0x55000000 | rgb;
    }

    /** Doofie-Rot — Ladebildschirm, Warnungen, negative Effekte. */
    public static final int RED = 0xFFBE1E28;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFAAAAAA;
}
