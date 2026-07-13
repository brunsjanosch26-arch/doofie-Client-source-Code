package de.doofie.bossraid;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DOOFIE BOSS-RAIDS — alle 2 Stunden erscheint ein Welt-Boss.
 *
 * — Boss: aufgemotzter Wither, Warden oder Ravager (300+ Herzen, Name,
 *   Glowing) nahe dem Spawn. Schaden wird pro Spieler getrackt.
 * — Beim Boss-Tod bekommen die Top-3-Schadensmacher Seelensplitter
 *   (3/2/1) + Diamanten, alle Beteiligten XP.
 * — Custom Items (alle mit item_model 'bossraid:<id>'):
 *   RAID-HORN: Rechtsklick beschwoert den Boss sofort (1h Cooldown).
 *   SEELENSPLITTER: Boss-Drop, Crafting-Zutat.
 *   SEELENKLINGE: 5 Splitter + Blaze-Rute = Netherite-Schwert mit
 *   Schaerfe VII und Fire Aspect II (macht +50% Schaden am Boss).
 *   SCHILD DER AHNEN: blockt einen Boss-Treffer KOMPLETT (60s Cooldown).
 * — TPA-System: /tpa /tpahere /tpaccept /tpadeny /tpaauto — /lobby zurueck.
 */
public class BossraidPlugin extends JavaPlugin {

    private BossManager boss;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        boss = new BossManager(this);
        getServer().getPluginManager().registerEvents(boss, this);
        boss.start();
        getCommand("raid").setExecutor(boss);
        getCommand("raid").setTabCompleter(boss);
        new TpaCommand(this, (von, zu) -> null).register();
        new LobbyCommand(this).register();
        getLogger().info("Boss-Raids aktiv — der erste Boss laesst nicht lange auf sich warten.");
    }

    public BossManager boss() {
        return boss;
    }
}
