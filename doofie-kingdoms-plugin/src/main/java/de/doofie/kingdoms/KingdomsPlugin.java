package de.doofie.kingdoms;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DOOFIE KINGDOMS — Koenigreiche, Land-Claims und Kriegs-Fenster.
 *
 * — /reich gruenden|einladen|beitreten|verlassen|claim|unclaim|info|liste|krieg
 * — Geclaimte Chunks sind fuer Fremde geschuetzt — ausser im Krieg
 *   (10-Minuten-Fenster zwischen zwei Reichen).
 * — Custom Items (alle mit item_model 'kingdoms:<id>'):
 *   KOENIGSKRONE: nur der Koenig — Buffs (Staerke/Speed) fuer alle
 *   Buerger im 16er-Umkreis, solange er sie traegt.
 *   BELAGERUNGS-STAMPFER: bricht im Krieg 3x3 Waende in feindlichen
 *   Claims (30s Cooldown).
 *   FRIEDENSVERTRAG: Rechtsklick durch den Koenig beendet alle Kriege
 *   seines Reichs sofort (wird verbraucht).
 * — TPA nur innerhalb des eigenen Koenigreichs! /lobby zurueck.
 */
public class KingdomsPlugin extends JavaPlugin {

    private KingdomManager reiche;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        reiche = new KingdomManager(this);
        getServer().getPluginManager().registerEvents(reiche, this);
        reiche.start();
        getCommand("reich").setExecutor(reiche);
        getCommand("reich").setTabCompleter(reiche);
        new TpaCommand(this, (von, zu) -> {
            String r1 = reiche.reichVon(von.getUniqueId());
            String r2 = reiche.reichVon(zu.getUniqueId());
            if (r1 == null || !r1.equals(r2)) {
                return "TPA geht nur innerhalb deines Koenigreichs — Feinde muessen laufen!";
            }
            return null;
        }).register();
        new LobbyCommand(this).register();
        getLogger().info("Kingdoms aktiv — auf dass die Reiche wachsen!");
    }

    @Override
    public void onDisable() {
        if (reiche != null) reiche.save();
    }

    public KingdomManager reiche() {
        return reiche;
    }
}
