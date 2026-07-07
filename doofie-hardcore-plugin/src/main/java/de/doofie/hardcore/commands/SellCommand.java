package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.listeners.SellMenuListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /sell — oeffnet das Verkaufsmenue (Items reinlegen und bestaetigen). */
public class SellCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public SellCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        SellMenuListener.openMenu(plugin, player);
        return true;
    }
}
