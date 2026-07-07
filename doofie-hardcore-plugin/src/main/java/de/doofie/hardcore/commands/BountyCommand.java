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

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /kopfgeld setzen <spieler> <betrag> — Kopfgeld setzen (Geld wird sofort abgezogen)
 * /kopfgeld liste — alle aktiven Kopfgelder
 */
public class BountyCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public BountyCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "liste";

        if (sub.equals("liste") || sub.equals("list")) {
            Map<UUID, Map<UUID, Double>> all = plugin.bounties().all();
            if (all.isEmpty()) {
                sender.sendMessage(Component.text("Aktuell gibt es keine Kopfgelder.", NamedTextColor.GRAY));
                return true;
            }
            sender.sendMessage(Component.text("── Aktive Kopfgelder ──", NamedTextColor.DARK_RED));
            all.keySet().forEach(target -> {
                OfflinePlayer p = Bukkit.getOfflinePlayer(target);
                double total = plugin.bounties().total(target);
                sender.sendMessage(Component.text()
                    .append(Component.text("  " + (p.getName() != null ? p.getName() : "?"), NamedTextColor.GOLD))
                    .append(Component.text(" — " + HardcorePlugin.dollar(total), NamedTextColor.RED))
                    .build());
            });
            return true;
        }

        // /kopfgeld setzen <spieler> <betrag> ODER direkt /kopfgeld <spieler> <betrag>
        if (sub.equals("setzen") || sub.equals("set")) {
            if (args.length < 3) {
                sender.sendMessage(Component.text("Nutzung: /kopfgeld setzen <spieler> <betrag>", NamedTextColor.RED));
                return true;
            }
            return setBounty(sender, args[1], args[2]);
        }
        if (args.length == 2) {
            return setBounty(sender, args[0], args[1]);
        }

        sender.sendMessage(Component.text("Nutzung: /kopfgeld <spieler> <betrag> — oder /kopfgeld liste", NamedTextColor.RED));
        return true;
    }

    private boolean setBounty(CommandSender sender, String targetName, String amountStr) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            player.sendMessage(Component.text("Spieler '" + targetName + "' ist nicht online.", NamedTextColor.RED));
            return true;
        }
        if (target.equals(player)) {
            player.sendMessage(Component.text("Auf dich selbst? Netter Versuch, Doofie.", NamedTextColor.RED));
            return true;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Ungueltiger Betrag: " + amountStr, NamedTextColor.RED));
            return true;
        }
        double min = plugin.getConfig().getDouble("min-kopfgeld", 100.0);
        if (amount < min) {
            player.sendMessage(Component.text("Mindest-Kopfgeld: " + HardcorePlugin.dollar(min), NamedTextColor.RED));
            return true;
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), amount)) {
            player.sendMessage(Component.text("Nicht genug Geld!", NamedTextColor.RED));
            return true;
        }

        plugin.bounties().add(target.getUniqueId(), player.getUniqueId(), amount);
        double total = plugin.bounties().total(target.getUniqueId());

        Bukkit.broadcast(Component.text()
            .append(Component.text("KOPFGELD! ", NamedTextColor.DARK_RED))
            .append(Component.text(player.getName(), NamedTextColor.GOLD))
            .append(Component.text(" hat " + HardcorePlugin.dollar(amount) + " auf ", NamedTextColor.GRAY))
            .append(Component.text(target.getName(), NamedTextColor.GOLD))
            .append(Component.text(" gesetzt! Gesamt: " + HardcorePlugin.dollar(total), NamedTextColor.RED))
            .build());
        return true;
    }
}
