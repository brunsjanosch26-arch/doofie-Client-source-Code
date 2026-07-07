package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.DuelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;

/**
 * Herzstueck des Kopfgeld-Systems:
 * - Duell-Tod: Gewinner bekommt den Pot, kein Bann.
 * - Kopfgeld-Tod: Killer kassiert, Kopf droppt (mit Trophaeen-Wert),
 *   Opfer wird gebannt — ausser es hat /schutz aktiv.
 * - Normaler Tod: Plugin hebt den Hardcore-Tod auf.
 */
public class DeathListener implements Listener {

    private final HardcorePlugin plugin;

    public DeathListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            plugin.stats().addKill(killer.getUniqueId());
        }

        // ── Duell-Tod: Pot an den Gewinner, kein Bann ──
        DuelManager.ActiveDuel duel = plugin.duels().duelOf(victim.getUniqueId());
        if (duel != null && killer != null
                && (killer.getUniqueId().equals(duel.a()) || killer.getUniqueId().equals(duel.b()))) {
            plugin.duels().endDuel(duel);
            plugin.economy().deposit(killer.getUniqueId(), duel.pot());
            Bukkit.broadcast(Component.text()
                .append(Component.text("DUELL VORBEI! ", NamedTextColor.RED))
                .append(Component.text(killer.getName(), NamedTextColor.GOLD))
                .append(Component.text(" besiegt " + victim.getName() + " und gewinnt ", NamedTextColor.GRAY))
                .append(Component.text(HardcorePlugin.dollar(duel.pot()), NamedTextColor.GOLD))
                .build());
            return;
        }

        double bounty = plugin.bounties().total(victim.getUniqueId());

        if (bounty > 0 && killer != null && !killer.equals(victim)) {
            // ── Kopfgeld-Kill ──
            plugin.bounties().claim(victim.getUniqueId());
            plugin.economy().deposit(killer.getUniqueId(), bounty);

            boolean protectedVictim = plugin.protection().isProtected(victim.getUniqueId());
            if (!protectedVictim) {
                plugin.bans().ban(victim.getUniqueId(), bounty);
            }

            // Kopf des Opfers droppen — als Trophaee mit eingebautem Wert
            double kopfBonus = plugin.getConfig().getDouble("kopf-bonus", 0.10);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(victim);
            meta.displayName(Component.text("Kopf von " + victim.getName(), NamedTextColor.GOLD));
            meta.getPersistentDataContainer().set(plugin.headKey(), PersistentDataType.DOUBLE, bounty * kopfBonus);
            head.setItemMeta(meta);
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);

            // Grosse Titel-Einblendung fuer alle
            Title title = Title.title(
                Component.text("KOPFGELD KASSIERT!", NamedTextColor.DARK_RED),
                Component.text(killer.getName() + " erledigt " + victim.getName() + " fuer " + HardcorePlugin.dollar(bounty),
                    NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800)));
            Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));

            if (protectedVictim) {
                Bukkit.broadcast(Component.text(
                    victim.getName() + " hatte Bounty-Schutz und wird NICHT gebannt!", NamedTextColor.AQUA));
            } else {
                double unbanCost = plugin.bans().unbanCost(victim.getUniqueId());
                Bukkit.broadcast(Component.text(
                    victim.getName() + " ist gebannt! Freikauf: " + HardcorePlugin.dollar(unbanCost), NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // Nach dem Respawn: Gebannte in den Spectator, alle anderen ueberleben
        // den Hardcore-Tod (Bann gibt es NUR ueber Kopfgeld).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (plugin.bans().isBanned(player.getUniqueId())) {
                player.setGameMode(GameMode.SPECTATOR);
                double cost = plugin.bans().unbanCost(player.getUniqueId());
                player.sendMessage(Component.text(
                    "Du wurdest per Kopfgeld gebannt! Freikauf: " + HardcorePlugin.dollar(cost)
                    + " — nutze /freikaufen (oder lass dich von einem Freund freikaufen).",
                    NamedTextColor.RED));
            } else {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }, 1L);
    }
}
