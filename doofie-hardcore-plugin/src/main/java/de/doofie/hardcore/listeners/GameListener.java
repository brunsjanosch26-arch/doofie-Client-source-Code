package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.DuelManager;
import de.doofie.hardcore.managers.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Quest-Fortschritt (Mob-Kills) und Duell-Abbruch beim Ausloggen. */
public class GameListener implements Listener {

    /** Opfer -> (Angreifer -> Zeit) fuer Kopfgeld-Splitting (letzte 10s). */
    public static final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, Long>> ASSISTS = new java.util.HashMap<>();

    private final HardcorePlugin plugin;

    public GameListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        // Friedenszonen: kein PvP
        if (plugin.extras().inZone(victim.getLocation()) || plugin.extras().inZone(attacker.getLocation())) {
            event.setCancelled(true);
            attacker.sendMessage(Component.text("Friedenszone — hier gilt Waffenruhe!", NamedTextColor.AQUA));
            return;
        }
        ASSISTS.computeIfAbsent(victim.getUniqueId(), v -> new java.util.HashMap<>())
            .put(attacker.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        plugin.quests().progress(killer, QuestManager.Type.KILL_MOBS, 1);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Duell-Flucht: Wer waehrend eines Duells offline geht, verliert den Pot
        DuelManager.ActiveDuel duel = plugin.duels().duelOf(event.getPlayer().getUniqueId());
        if (duel == null) return;
        plugin.duels().endDuel(duel);
        var winnerId = duel.a().equals(event.getPlayer().getUniqueId()) ? duel.b() : duel.a();
        plugin.economy().deposit(winnerId, duel.pot());
        Player winner = Bukkit.getPlayer(winnerId);
        Bukkit.broadcast(Component.text()
            .append(Component.text("DUELL-FLUCHT! ", NamedTextColor.RED))
            .append(Component.text(event.getPlayer().getName() + " ist geflohen — ", NamedTextColor.GRAY))
            .append(Component.text((winner != null ? winner.getName() : "der Gegner") + " gewinnt "
                + HardcorePlugin.dollar(duel.pot()), NamedTextColor.GOLD))
            .build());
    }
}
