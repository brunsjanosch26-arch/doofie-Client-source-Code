package de.doofie;

/**
 * Zentrales Design-Token-System der Doofie-Mod.
 * Alle HUD-Elemente nutzen diese Farben, damit das Branding ueberall einheitlich ist.
 */
public final class DoofieTheme {

    private DoofieTheme() {}

    /** Halbtransparenter Standard-Hintergrund aller HUD-Boxen. */
    public static final int BG = 0x88000000;

    /** Doofie-Akzent (Gold) — aktive Elemente, Highlights. */
    public static final int ACCENT = 0xDDFFC940;

    /** Doofie-Akzent halbtransparent — z.B. positive Statuseffekte. */
    public static final int ACCENT_SOFT = 0x55FFC940;

    /** Doofie-Rot — Ladebildschirm, Warnungen, negative Effekte. */
    public static final int RED = 0xFFBE1E28;

    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFAAAAAA;
}
