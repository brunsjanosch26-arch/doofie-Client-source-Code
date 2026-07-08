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

    // ── Bounty-Versteigerung (eine gleichzeitig) ──
    private UUID auctionTarget = null;
    private UUID highBidder = null;
    private double highBid = 0;
    private long auctionEnd = 0;

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

        // ── /kopfgeld auktion <spieler> <startgebot> ──
        if (sub.equals("auktion")) {
            if (!(sender instanceof Player player)) return true;
            if (auctionTarget != null && System.currentTimeMillis() < auctionEnd) {
                player.sendMessage(Component.text("Es laeuft schon eine Auktion!", NamedTextColor.RED));
                return true;
            }
            if (args.length < 3) {
                player.sendMessage(Component.text("Nutzung: /kopfgeld auktion <spieler> <startgebot>", NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            double start;
            try { start = Double.parseDouble(args[2]); } catch (NumberFormatException e) { return true; }
            if (target == null || target.equals(player) || start < plugin.getConfig().getDouble("min-kopfgeld", 100.0)) {
                player.sendMessage(Component.text("Ungueltiges Ziel oder Startgebot zu niedrig.", NamedTextColor.RED));
                return true;
            }
            if (!plugin.economy().has(player.getUniqueId(), start)) {
                player.sendMessage(Component.text("Du kannst dein eigenes Startgebot nicht zahlen!", NamedTextColor.RED));
                return true;
            }
            auctionTarget = target.getUniqueId();
            highBidder = player.getUniqueId();
            highBid = start;
            auctionEnd = System.currentTimeMillis() + 5 * 60_000L;
            Bukkit.broadcast(Component.text("KOPFGELD-AUKTION auf " + target.getName() + "! Startgebot "
                + HardcorePlugin.dollar(start) + " von " + player.getName()
                + " — /kopfgeld bieten <betrag> (5 Minuten!)", NamedTextColor.DARK_RED));
            Bukkit.getScheduler().runTaskLater(plugin, this::endAuction, 20L * 300);
            return true;
        }

        // ── /kopfgeld bieten <betrag> ──
        if (sub.equals("bieten")) {
            if (!(sender instanceof Player player)) return true;
            if (auctionTarget == null || System.currentTimeMillis() >= auctionEnd) {
                player.sendMessage(Component.text("Keine Auktion aktiv.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) return true;
            double bid;
            try { bid = Double.parseDouble(args[1]); } catch (NumberFormatException e) { return true; }
            if (bid <= highBid) {
                player.sendMessage(Component.text("Du musst mehr als " + HardcorePlugin.dollar(highBid) + " bieten!", NamedTextColor.RED));
                return true;
            }
            if (!plugin.economy().has(player.getUniqueId(), bid)) {
                player.sendMessage(Component.text("So viel hast du nicht!", NamedTextColor.RED));
                return true;
            }
            highBidder = player.getUniqueId();
            highBid = bid;
            Bukkit.broadcast(Component.text(player.getName() + " bietet " + HardcorePlugin.dollar(bid)
                + " auf das Kopfgeld!", NamedTextColor.RED));
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

    /** Auktionsende: Hoechstbietender zahlt, Kopfgeld wird gesetzt. */
    private void endAuction() {
        if (auctionTarget == null) return;
        UUID target = auctionTarget, bidder = highBidder;
        double bid = highBid;
        auctionTarget = null;
        highBidder = null;
        highBid = 0;

        if (bidder == null || !plugin.economy().withdraw(bidder, bid)) {
            Bukkit.broadcast(Component.text("Auktion beendet — der Bieter konnte nicht zahlen. Nichts passiert.", NamedTextColor.GRAY));
            return;
        }
        plugin.bounties().add(target, bidder, bid);
        Player t = Bukkit.getPlayer(target);
        if (t != null) placeWantedSign(t);
        Bukkit.broadcast(Component.text("AUKTION VORBEI! " + Bukkit.getOfflinePlayer(bidder).getName()
            + " setzt " + HardcorePlugin.dollar(bid) + " Kopfgeld auf "
            + Bukkit.getOfflinePlayer(target).getName() + "!", NamedTextColor.DARK_RED));
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
