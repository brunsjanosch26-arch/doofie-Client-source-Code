package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.DuelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /duell <spieler> <einsatz> — herausfordern
 * /duell annehmen — Herausforderung annehmen (beide zahlen ein, Gewinner kriegt alles)
 */
public class DuellCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public DuellCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        var duels = plugin.duels();

        if (duels.inDuel(player.getUniqueId())) {
            player.sendMessage(Component.text("Du bist schon in einem Duell!", NamedTextColor.RED));
            return true;
        }

        if (args.length >= 1 && args[0].toLowerCase(Locale.ROOT).equals("annehmen")) {
            DuelManager.Request req = duels.pendingFor(player.getUniqueId());
            if (req == null) {
                player.sendMessage(Component.text("Keine offene Herausforderung.", NamedTextColor.RED));
                return true;
            }
            Player challenger = Bukkit.getPlayer(req.challenger());
            if (challenger == null || duels.inDuel(challenger.getUniqueId())) {
                player.sendMessage(Component.text("Der Herausforderer ist nicht mehr verfuegbar.", NamedTextColor.RED));
                return true;
            }
            double stake = req.stake();
            if (!plugin.economy().has(player.getUniqueId(), stake)) {
                player.sendMessage(Component.text("Du hast den Einsatz nicht! (" + HardcorePlugin.dollar(stake) + ")", NamedTextColor.RED));
                return true;
            }
            if (!plugin.economy().withdraw(challenger.getUniqueId(), stake)) {
                player.sendMessage(Component.text("Der Herausforderer hat den Einsatz nicht mehr.", NamedTextColor.RED));
                return true;
            }
            plugin.economy().withdraw(player.getUniqueId(), stake);
            duels.startDuel(challenger.getUniqueId(), player.getUniqueId(), stake * 2);

            Bukkit.broadcast(Component.text()
                .append(Component.text("DUELL! ", NamedTextColor.RED))
                .append(Component.text(challenger.getName() + " vs " + player.getName(), NamedTextColor.GOLD))
                .append(Component.text(" um " + HardcorePlugin.dollar(stake * 2) + " — moege der Bessere gewinnen!", NamedTextColor.GRAY))
                .build());
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text("Nutzung: /duell <spieler> <einsatz> — oder /duell annehmen", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            player.sendMessage(Component.text("Spieler nicht gefunden.", NamedTextColor.RED));
            return true;
        }
        double stake;
        try {
            stake = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Ungueltiger Einsatz.", NamedTextColor.RED));
            return true;
        }
        if (stake <= 0 || !plugin.economy().has(player.getUniqueId(), stake)) {
            player.sendMessage(Component.text("Du hast den Einsatz nicht!", NamedTextColor.RED));
            return true;
        }

        duels.request(player.getUniqueId(), target.getUniqueId(), stake);
        player.sendMessage(Component.text("Herausforderung an " + target.getName() + " gesendet (" + HardcorePlugin.dollar(stake) + ").", NamedTextColor.GREEN));
        target.sendMessage(Component.text()
            .append(Component.text("DUELL-HERAUSFORDERUNG! ", NamedTextColor.RED))
            .append(Component.text(player.getName() + " fordert dich um " + HardcorePlugin.dollar(stake) + " heraus. ", NamedTextColor.GRAY))
            .append(Component.text("/duell annehmen", NamedTextColor.GOLD))
            .append(Component.text(" (60s gueltig)", NamedTextColor.GRAY))
            .build());
        return true;
    }
}
