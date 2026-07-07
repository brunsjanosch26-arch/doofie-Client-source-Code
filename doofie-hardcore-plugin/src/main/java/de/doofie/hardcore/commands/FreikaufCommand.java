package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /freikaufen — sich selbst freikaufen (Kopfgeld + 5%)
 * /freikaufen <spieler> — einen anderen gebannten Spieler freikaufen
 */
public class FreikaufCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public FreikaufCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }

        OfflinePlayer target = args.length > 0 ? Bukkit.getOfflinePlayer(args[0]) : payer;

        if (!plugin.bans().isBanned(target.getUniqueId())) {
            payer.sendMessage(Component.text(
                (target.equals(payer) ? "Du bist" : target.getName() + " ist") + " nicht gebannt.",
                NamedTextColor.RED));
            return true;
        }

        double cost = plugin.bans().unbanCost(target.getUniqueId());
        if (!plugin.economy().withdraw(payer.getUniqueId(), cost)) {
            payer.sendMessage(Component.text(
                "Nicht genug Geld! Freikauf kostet " + HardcorePlugin.dollar(cost)
                + " (du hast " + HardcorePlugin.dollar(plugin.economy().get(payer.getUniqueId())) + ")",
                NamedTextColor.RED));
            return true;
        }

        plugin.bans().unban(target.getUniqueId());

        // Wenn online: zurueck in den Survival-Modus — am Bett, sonst am Welt-Spawn
        Player online = target.getPlayer();
        if (online != null) {
            online.setGameMode(GameMode.SURVIVAL);
            var respawn = online.getRespawnLocation();
            online.teleport(respawn != null ? respawn : online.getWorld().getSpawnLocation());
            online.sendMessage(Component.text("Du bist frei! Willkommen zurueck.", NamedTextColor.GREEN));
        }

        Bukkit.broadcast(Component.text()
            .append(Component.text("FREIGEKAUFT! ", NamedTextColor.GREEN))
            .append(Component.text(payer.getName(), NamedTextColor.GOLD))
            .append(Component.text(" hat " + (target.equals(payer) ? "sich selbst" : String.valueOf(target.getName()))
                + " fuer " + HardcorePlugin.dollar(cost) + " freigekauft!", NamedTextColor.GRAY))
            .build());
        return true;
    }
}
