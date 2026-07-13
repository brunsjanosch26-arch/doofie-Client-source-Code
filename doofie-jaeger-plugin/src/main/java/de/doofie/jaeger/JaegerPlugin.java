package de.doofie.jaeger;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DOOFIE KOPFGELDJAEGER 2.0 — jeder Kill macht dich wertvoller.
 *
 * — Jeder Spieler-Kill erhoeht dein EIGENES Kopfgeld um 100$.
 *   Wer dich dann erlegt, kassiert dein komplettes Kopfgeld.
 * — /kopfgeld [spieler] zeigt Kopfgelder, /geld dein Konto.
 * — Custom Items (alle mit item_model 'jaeger:<id>'):
 *   JAEGER-FERNROHR: zeigt Richtung + Distanz zum wertvollsten
 *   Spieler in der Naehe (10s Cooldown).
 *   TARNUMHANG: Rechtsklick = 5 Minuten unsichtbar fuers Fernrohr
 *   (15 Min Cooldown).
 *   BLUTSIEGEL: Rechtsklick auf einen Spieler = 10 Minuten lang kassierst
 *   NUR DU sein Kopfgeld (wird verbraucht).
 * — TPA mit Anti-Abuse: kein /tpa zu deinem versiegelten Jagdziel!
 *   /lobby zurueck.
 */
public class JaegerPlugin extends JavaPlugin {

    private JagdManager jagd;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        jagd = new JagdManager(this);
        getServer().getPluginManager().registerEvents(jagd, this);
        jagd.start();
        for (String c : new String[]{"kopfgeld", "geld"}) {
            getCommand(c).setExecutor(jagd);
            getCommand(c).setTabCompleter(jagd);
        }
        new TpaCommand(this, (von, zu) -> {
            if (jagd.istJagdziel(von.getUniqueId(), zu.getUniqueId())) {
                return "Kein TPA zu deinem Jagdziel — jage es zu Fuss!";
            }
            return null;
        }).register();
        new LobbyCommand(this).register();
        getLogger().info("Kopfgeldjaeger 2.0 aktiv — gute Jagd!");
    }

    @Override
    public void onDisable() {
        if (jagd != null) jagd.save();
    }

    public JagdManager jagd() {
        return jagd;
    }
}
