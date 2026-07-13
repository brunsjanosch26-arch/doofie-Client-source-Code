package de.doofie.skyblock;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DOOFIE SKYBLOCK-WARS — Himmelsinseln mit Insel-Kern.
 *
 * — Beim ersten Join bekommt jeder eine eigene Insel (Grid, 512 Bloecke
 *   Abstand, y=120) mit Insel-Kern (Lodestone) in der Mitte.
 * — Insel-Level steigt durch Bloecke platzieren auf der eigenen Insel.
 * — Wer in die Leere faellt, landet zuhause statt zu sterben.
 * — Custom Items (alle mit item_model 'skyblock:<id>'):
 *   INSEL-KOMPASS: Rechtsklick = heim teleportieren (3s Warmup).
 *   VOID-ANGEL: Rechtsklick = fischt zufaellige Beute aus der Leere (30s CD).
 *   KERN-BRECHER: einzige Spitzhacke, die FREMDE Insel-Kerne brechen kann —
 *   stiehlt 25% des Insel-Levels und droppt Diamanten.
 * — TPA-System: /tpa /tpahere /tpaccept /tpadeny /tpaauto — /lobby zurueck.
 */
public class SkyblockPlugin extends JavaPlugin {

    private IslandManager islands;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        islands = new IslandManager(this);
        getServer().getPluginManager().registerEvents(islands, this);
        islands.start();
        getCommand("insel").setExecutor(islands);
        getCommand("insel").setTabCompleter(islands);
        new TpaCommand(this, (von, zu) -> null).register();
        new LobbyCommand(this).register();
        getLogger().info("Skyblock-Wars aktiv — ab in die Luft!");
    }

    @Override
    public void onDisable() {
        if (islands != null) islands.save();
    }

    public IslandManager islands() {
        return islands;
    }
}
