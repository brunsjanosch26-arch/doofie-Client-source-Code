package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MoneyCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public MoneyCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length >= 1) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            sender.sendMessage(Component.text(
                target.getName() + " hat " + HardcorePlugin.dollar(plugin.economy().get(target.getUniqueId())),
                NamedTextColor.GOLD));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        player.sendMessage(Component.text(
            "Dein Guthaben: " + HardcorePlugin.dollar(plugin.economy().get(player.getUniqueId())),
            NamedTextColor.GOLD));
        return true;
    }
}
