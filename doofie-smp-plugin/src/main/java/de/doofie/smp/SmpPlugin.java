package de.doofie.smp;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * DOOFIE SMP — der klassische Survival-Modus des Netzwerks.
 *
 * Normale Welt, normales Ueberleben — plus:
 * — TPA-System: /tpa /tpahere /tpaccept /tpadeny /tpaauto
 * — /lobby zurueck in die Netzwerk-Lobby
 */
public class SmpPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        new TpaCommand(this, (von, zu) -> null).register();
        new LobbyCommand(this).register();
        getLogger().info("Doofie-SMP aktiv — frohes Ueberleben!");
    }
}
