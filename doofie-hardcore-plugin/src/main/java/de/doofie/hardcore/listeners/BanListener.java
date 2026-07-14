package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Locale;
import java.util.Set;

/** Haelt gebannte Spieler im Spectator und erlaubt nur Freikauf-Commands. */
public class BanListener implements Listener {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
        "freikaufen", "unbanme", "money", "geld", "balance", "bal", "kopfgeld", "bounty", "gerichtsduell", "testament", "hilfe"
    );

    private final HardcorePlugin plugin;

    public BanListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // SMP-Modus: haengengebliebene Spectator zurueck in den Survival holen
        if (!plugin.getConfig().getBoolean("hardcore-bann", true)) {
            if (player.getGameMode() == GameMode.SPECTATOR && !player.isOp()) {
                player.setGameMode(GameMode.SURVIVAL);
                player.sendMessage(Component.text("Willkommen zurueck im Survival!", NamedTextColor.GREEN));
            }
            return;
        }
        if (plugin.bans().isDead(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(Component.text("Du bist noch tot! /freikaufen bringt dich gratis zurueck.", NamedTextColor.YELLOW));
            return;
        }
        if (plugin.bans().isBanned(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            double cost = plugin.bans().unbanCost(player.getUniqueId());
            player.sendMessage(Component.text()
                .append(Component.text("Du bist per Kopfgeld gebannt! ", NamedTextColor.DARK_RED))
                .append(Component.text("Freikauf: " + HardcorePlugin.dollar(cost)
                    + " — nutze /freikaufen. Freunde koennen dich mit /freikaufen "
                    + player.getName() + " freikaufen.", NamedTextColor.RED))
                .build());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!plugin.bans().isBanned(player.getUniqueId()) && !plugin.bans().isDead(player.getUniqueId())) return;

        String cmd = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (!ALLOWED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            player.sendMessage(Component.text(
                "Gebannt! Erlaubt sind nur: /freikaufen, /money, /kopfgeld liste", NamedTextColor.RED));
        }
    }
}
