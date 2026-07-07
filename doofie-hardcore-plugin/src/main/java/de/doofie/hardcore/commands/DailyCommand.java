package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /daily — taegliche Belohnung mit Streak-Bonus. */
public class DailyCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public DailyCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        double reward = plugin.daily().claim(player.getUniqueId());
        if (reward < 0) {
            player.sendMessage(Component.text("Heute schon abgeholt! Komm morgen wieder. (Streak: "
                + plugin.daily().streak(player.getUniqueId()) + " Tage)", NamedTextColor.RED));
            return true;
        }
        plugin.economy().deposit(player.getUniqueId(), reward);
        int streak = plugin.daily().streak(player.getUniqueId());
        player.sendMessage(Component.text()
            .append(Component.text("DAILY! ", NamedTextColor.GREEN))
            .append(Component.text("+" + HardcorePlugin.dollar(reward), NamedTextColor.GOLD))
            .append(Component.text(" (Streak: " + streak + " Tag" + (streak == 1 ? "" : "e")
                + (streak >= 7 ? " — MAX-BONUS!" : ", ab 7 Tagen gibt es den Grossbonus") + ")", NamedTextColor.GRAY))
            .build());
        return true;
    }
}
