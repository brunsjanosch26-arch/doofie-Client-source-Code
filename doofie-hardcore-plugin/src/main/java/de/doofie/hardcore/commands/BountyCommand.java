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
        // Rache-Rabatt: auf den eigenen Killer zahlt man nur die Haelfte
        double toPay = amount;
        if (plugin.extras().hasRevengeDiscount(player.getUniqueId(), target.getUniqueId())) {
            toPay = amount / 2;
            player.sendMessage(Component.text("RACHE-RABATT: Du zahlst nur " + HardcorePlugin.dollar(toPay)
                + " fuer " + HardcorePlugin.dollar(amount) + " Kopfgeld!", NamedTextColor.LIGHT_PURPLE));
        }
        if (!plugin.economy().withdraw(player.getUniqueId(), toPay)) {
            player.sendMessage(Component.text("Nicht genug Geld!", NamedTextColor.RED));
            return true;
        }

        plugin.bounties().add(target.getUniqueId(), player.getUniqueId(), amount);
        placeWantedSign(target);
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

    /** Fahndungsplakat am Spawn ab 1000$ Gesamt-Kopfgeld. */
    private void placeWantedSign(Player target) {
        double total = plugin.bounties().total(target.getUniqueId());
        if (total < 1000) return;
        var world = target.getWorld();
        var spawn = world.getSpawnLocation();
        int slot = Math.abs(target.getUniqueId().hashCode()) % 8;
        var block = world.getBlockAt(spawn.getBlockX() + 2 + slot, spawn.getBlockY() + 1, spawn.getBlockZ() + 2);
        block.setType(org.bukkit.Material.OAK_SIGN);
        if (block.getState() instanceof org.bukkit.block.Sign sign) {
            var side = sign.getSide(org.bukkit.block.sign.Side.FRONT);
            side.line(0, Component.text("=== WANTED ===", NamedTextColor.DARK_RED));
            side.line(1, Component.text(target.getName(), NamedTextColor.GOLD));
            side.line(2, Component.text(HardcorePlugin.dollar(total), NamedTextColor.RED));
            side.line(3, Component.text("tot. Einfach tot.", NamedTextColor.GRAY));
            sign.update();
        }
    }
}
