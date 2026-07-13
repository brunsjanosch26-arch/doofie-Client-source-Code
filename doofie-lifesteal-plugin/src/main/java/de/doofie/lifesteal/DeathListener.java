package de.doofie.lifesteal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

/**
 * Die Lifesteal-Kernmechanik:
 *
 * — Stirbt ein Spieler durch einen ANDEREN Spieler, verliert er 1 Herz
 *   und ein Herz-Item droppt am Todesort. Der Killer (oder wer immer
 *   es aufhebt) kann es per Rechtsklick einloesen.
 * — Stirbt er natuerlich (Lava, Fall, Mobs, ...), verliert er auch
 *   1 Herz — aber es droppt NICHTS, das Herz ist einfach weg.
 * — Faellt er dadurch auf 0 Herzen, wird er nach dem Respawn eliminiert
 *   (Zuschauer-Modus, bis /revive).
 */
public class DeathListener implements Listener {

    private final LifestealPlugin plugin;

    public DeathListener(LifestealPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player opfer = event.getEntity();
        HeartManager hearts = plugin.getHeartManager();
        if (hearts.istEliminiert(opfer.getUniqueId())) return;

        Player killer = opfer.getKiller();
        boolean pvp = killer != null && !killer.getUniqueId().equals(opfer.getUniqueId());

        boolean eliminiert = hearts.removeHerz(opfer.getUniqueId());

        if (pvp) {
            // Herz droppt am Todesort — wer es aufhebt, loest es per Rechtsklick ein
            event.getDrops().add(plugin.getItems().herz());
            killer.sendMessage(Component.text("❤ " + opfer.getName()
                + " hat ein Herz verloren — schnapp es dir!", NamedTextColor.GOLD));
        }
        opfer.sendMessage(Component.text("Du hast ein Herz verloren! Verbleibend: "
            + hearts.getHerzen(opfer.getUniqueId()), NamedTextColor.RED));

        if (eliminiert) {
            // Elimination erst nach dem Respawn durchsetzen (sonst haengt der Todesbildschirm)
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (opfer.isOnline()) hearts.eliminiere(opfer);
            }, 1L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin,
            () -> plugin.getHeartManager().handleJoin(p), 1L);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getHeartManager().handleJoin(event.getPlayer());
    }
}
