package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /schutz — teure Immunitaet gegen den Kopfgeld-Bann (Kill zahlt trotzdem aus). */
public class SchutzCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public SchutzCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        if (plugin.protection().isProtected(player.getUniqueId())) {
            player.sendMessage(Component.text("Du bist noch " + plugin.protection().remainingSeconds(player.getUniqueId())
                + "s geschuetzt.", NamedTextColor.RED));
            return true;
        }
        double cost = plugin.getConfig().getDouble("schutz-kosten", 2000.0);
        long minutes = plugin.getConfig().getLong("schutz-dauer-minuten", 30);
        if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("Nicht genug Geld! Schutz kostet " + HardcorePlugin.dollar(cost), NamedTextColor.RED));
            return true;
        }
        plugin.protection().protect(player.getUniqueId(), minutes);
        player.sendMessage(Component.text()
            .append(Component.text("SCHUTZ AKTIV! ", NamedTextColor.AQUA))
            .append(Component.text(minutes + " Minuten immun gegen den Kopfgeld-Bann. Sterben tust du trotzdem!", NamedTextColor.GRAY))
            .build());
        Bukkit.broadcast(Component.text(player.getName() + " hat sich Bounty-Schutz gekauft — Feigling oder Genie?", NamedTextColor.GRAY));
        return true;
    }
}
