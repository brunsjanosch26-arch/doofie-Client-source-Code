package de.doofie.zombie;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * DOOFIE ZOMBIE-APOKALYPSE — nachts wird die Welt zur Hoelle.
 *
 * — Nachts spawnen Zombie-Horden um jeden Spieler: SPRINTER (schnell),
 *   TANK (20 Herzen + Resistenz) und SPUCKER (vergiftet beim Treffer).
 * — Jede 7. Nacht ist BLUTMOND: doppelte Horden mit Staerke!
 * — Tagsueber ist Ruhe — Zeit zum Bauen.
 * — Custom Items (alle mit item_model 'zombie:<id>'):
 *   STACHELDRAHT: Rechtsklick auf einen Block — platziert ein Spinnennetz,
 *   das Monster verletzt.
 *   SELBSTSCHUSS-TURM: Rechtsklick auf einen WERFER — er feuert alle 2s
 *   selbststaendig Pfeile auf Monster im 12er-Umkreis.
 *   ADRENALIN-SPRITZE: 30s Speed II + Staerke II — danach 30s grosser Hunger.
 * — TPA nur TAGSUEBER — nachts ist jeder auf sich gestellt! /lobby zurueck.
 */
public class ZombiePlugin extends JavaPlugin {

    private ApokalypseManager apokalypse;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        apokalypse = new ApokalypseManager(this);
        getServer().getPluginManager().registerEvents(apokalypse, this);
        apokalypse.start();
        getCommand("apokalypse").setExecutor(apokalypse);
        getCommand("apokalypse").setTabCompleter(apokalypse);
        new TpaCommand(this, (von, zu) -> {
            long zeit = von.getWorld().getTime();
            if (zeit >= 13000 && zeit <= 23000) {
                return "Nachts gibt es kein TPA — ueberlebe bis zum Morgen!";
            }
            return null;
        }).register();
        new LobbyCommand(this).register();
        getLogger().info("Zombie-Apokalypse aktiv — die Nacht wird ungemuetlich.");
    }

    public ApokalypseManager apokalypse() {
        return apokalypse;
    }
}
