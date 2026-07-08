package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /wette <spieler> <betrag> — nur waehrend der 30s nach Duellstart. */
public class WetteCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public WetteCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        if (!plugin.duels().betsOpen()) {
            p.sendMessage(Component.text("Kein Wettfenster offen — wetten geht nur 30s nach Duellstart!", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) { p.sendMessage(Component.text("Nutzung: /wette <spieler> <betrag>", NamedTextColor.RED)); return true; }
        Player on = Bukkit.getPlayerExact(args[0]);
        if (on == null || !plugin.duels().inDuel(on.getUniqueId())) {
            p.sendMessage(Component.text("Dieser Spieler ist nicht im Duell.", NamedTextColor.RED));
            return true;
        }
        if (plugin.duels().inDuel(p.getUniqueId())) {
            p.sendMessage(Component.text("Duellanten wetten nicht!", NamedTextColor.RED));
            return true;
        }
        double amount;
        try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException e) { return true; }
        if (amount <= 0 || !plugin.economy().withdraw(p.getUniqueId(), amount)) {
            p.sendMessage(Component.text("Nicht genug Geld!", NamedTextColor.RED));
            return true;
        }
        plugin.duels().addBet(p.getUniqueId(), on.getUniqueId(), amount);
        p.sendMessage(Component.text("Wette platziert: " + HardcorePlugin.dollar(amount) + " auf " + on.getName(), NamedTextColor.GREEN));
        return true;
    }
}
