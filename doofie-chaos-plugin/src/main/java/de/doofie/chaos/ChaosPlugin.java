package de.doofie.chaos;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DOOFIE CHAOS-EVENTS — alle 15 Minuten wuerfelt der Server.
 *
 * — Events: Item-Regen, Inventar-Tausch, Low Gravity, TNT-Hagel,
 *   Mob-Party, XP-Regen, Speed-Runde.
 * — Custom Items (alle mit item_model 'chaos:<id>'):
 *   CHAOS-WUERFEL: Rechtsklick loest SOFORT ein Zufalls-Event aus
 *   (1h Cooldown).
 *   STABILISATOR: liegt er im Inventar, ueberspringt dich das naechste
 *   negative Event (wird verbraucht).
 *   GLUECKSTRANK: Rechtsklick — beim naechsten Event bist du garantiert
 *   auf der Gewinnerseite (wird verbraucht).
 * — TPA-System: /tpa /tpahere /tpaccept /tpadeny /tpaauto — /lobby zurueck.
 */
public class ChaosPlugin extends JavaPlugin {

    private ChaosManager chaos;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        chaos = new ChaosManager(this);
        getServer().getPluginManager().registerEvents(chaos, this);
        chaos.start();
        getCommand("chaos").setExecutor(chaos);
        getCommand("chaos").setTabCompleter(chaos);
        new TpaCommand(this, (von, zu) -> null).register();
        new LobbyCommand(this).register();
        getLogger().info("Chaos-Events aktiv — nichts ist sicher.");
    }

    public ChaosManager chaos() {
        return chaos;
    }
}
