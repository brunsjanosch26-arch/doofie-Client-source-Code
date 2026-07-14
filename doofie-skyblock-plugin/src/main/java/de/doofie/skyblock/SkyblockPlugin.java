package de.doofie.skyblock;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DOOFIE SKYBLOCK-WARS — die 10-Insel-Karte mit Session-Reset.
 *
 * — Feste Karte: 5x die grosse Insel (aussen) + 5x die klassische
 *   Skyblock-Insel (innen) im Ring um die Spawn-Plattform.
 * — Alle Kisten sind mit Zufalls-Loot befuellt.
 * — RESET: Verlaesst der letzte Spieler den Server (z.B. /lobby),
 *   wird alles Zurueckgesetzt — abgebaut, platziert, gelootet.
 * — Custom Items (alle mit item_model 'skyblock:<id>'):
 *   INSEL-KOMPASS, VOID-ANGEL, KERN-BRECHER (Effizienz X).
 * — TPA-System: /tpa /tpahere /tpaccept /tpadeny /tpaauto — /lobby zurueck.
 */
public class SkyblockPlugin extends JavaPlugin {

    private IslandManager islands;
    private KartenManager karte;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        islands = new IslandManager(this);
        karte = new KartenManager(this);
        getServer().getPluginManager().registerEvents(islands, this);
        getServer().getPluginManager().registerEvents(karte, this);
        islands.start();
        try {
            karte.start();
        } catch (Exception ex) {
            getLogger().severe("Karte konnte nicht geladen werden: " + ex.getMessage());
        }
        getCommand("insel").setExecutor(islands);
        getCommand("insel").setTabCompleter(islands);
        new TpaCommand(this, (von, zu) -> null).register();
        new LobbyCommand(this).register();
        getLogger().info("Skyblock-Wars aktiv — 10 Inseln, Reset beim Verlassen!");
    }

    public IslandManager islands() {
        return islands;
    }

    public KartenManager karte() {
        return karte;
    }
}
