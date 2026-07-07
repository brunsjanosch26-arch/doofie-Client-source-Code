package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

/**
 * Herzstueck des Kopfgeld-Systems:
 * - Stirbt ein Spieler MIT Kopfgeld durch einen anderen Spieler:
 *   Killer bekommt das Kopfgeld, der Kopf des Opfers droppt, Opfer wird gebannt.
 * - Stirbt ein Spieler OHNE Kopfgeld: normaler Respawn (Plugin macht
 *   den Hardcore-Tod rueckgaengig — gebannt wird man NUR per Kopfgeld).
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

        double bounty = plugin.bounties().total(victim.getUniqueId());

        if (bounty > 0 && killer != null && !killer.equals(victim)) {
            // ── Kopfgeld-Kill ──
            plugin.bounties().claim(victim.getUniqueId());
            plugin.economy().deposit(killer.getUniqueId(), bounty);
            plugin.bans().ban(victim.getUniqueId(), bounty);

            // Kopf des Opfers droppen
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(victim);
            meta.displayName(Component.text("Kopf von " + victim.getName(), NamedTextColor.GOLD));
            head.setItemMeta(meta);
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);

            double unbanCost = plugin.bans().unbanCost(victim.getUniqueId());
            Bukkit.broadcast(Component.text()
                .append(Component.text("KOPFGELD! ", NamedTextColor.DARK_RED))
                .append(Component.text(killer.getName(), NamedTextColor.GOLD))
                .append(Component.text(" hat ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName(), NamedTextColor.GOLD))
                .append(Component.text(" erledigt und " + HardcorePlugin.dollar(bounty) + " kassiert! ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName() + " ist gebannt (Freikauf: " + HardcorePlugin.dollar(unbanCost) + ")", NamedTextColor.RED))
                .build());
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
