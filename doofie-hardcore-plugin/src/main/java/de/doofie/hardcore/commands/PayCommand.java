package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class PayCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public PayCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(Component.text("Nutzung: /pay <spieler> <betrag>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            player.sendMessage(Component.text("Spieler nicht online.", NamedTextColor.RED));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Ungueltiger Betrag.", NamedTextColor.RED));
            return true;
        }
        if (amount <= 0) {
            player.sendMessage(Component.text("Der Betrag muss positiv sein.", NamedTextColor.RED));
            return true;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), amount)) {
            player.sendMessage(Component.text("Nicht genug Geld!", NamedTextColor.RED));
            return true;
        }
        plugin.economy().deposit(target.getUniqueId(), amount);
        player.sendMessage(Component.text("Du hast " + HardcorePlugin.dollar(amount) + " an " + target.getName() + " gesendet.", NamedTextColor.GREEN));
        target.sendMessage(Component.text(player.getName() + " hat dir " + HardcorePlugin.dollar(amount) + " gesendet!", NamedTextColor.GREEN));
        return true;
    }
}
