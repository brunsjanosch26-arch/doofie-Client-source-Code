package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.SmpExtras;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** /pass, /statistik, /zone — die Bounty-SMP-Zusatzcommands. */
public class SmpCommands implements CommandExecutor {

    private final HardcorePlugin plugin;

    public SmpCommands(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        var ex = plugin.extras();

        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "pass" -> {
                if (args.length >= 1 && args[0].equalsIgnoreCase("premium")) {
                    if (ex.isPremium(p.getUniqueId())) { p.sendMessage(Component.text("Du hast schon Premium!", NamedTextColor.RED)); return true; }
                    double cost = plugin.getConfig().getDouble("pass-premium-kosten", 5000.0);
                    if (!plugin.economy().withdraw(p.getUniqueId(), cost)) {
                        p.sendMessage(Component.text("Premium kostet " + HardcorePlugin.dollar(cost), NamedTextColor.RED));
                        return true;
                    }
                    ex.setPremium(p.getUniqueId());
                    Bukkit.broadcast(Component.text(p.getName() + " hat den PREMIUM-Season-Pass gekauft!", NamedTextColor.LIGHT_PURPLE));
                    return true;
                }
                int lvl = ex.level(p.getUniqueId());
                int prog = ex.xpOf(p.getUniqueId()) % 100;
                p.sendMessage(Component.text("── SEASON-PASS ──", NamedTextColor.LIGHT_PURPLE));
                p.sendMessage(Component.text("Level " + lvl + " (" + prog + "/100 XP) — "
                    + (ex.isPremium(p.getUniqueId()) ? "PREMIUM (3x Belohnung)" : "Gratis-Stufe"), NamedTextColor.GRAY));
                p.sendMessage(Component.text("XP: Kills +10, Quests +50, /sell +1 pro Item. Jedes Level: "
                    + (ex.isPremium(p.getUniqueId()) ? "300$" : "100$"), NamedTextColor.GRAY));
                if (!ex.isPremium(p.getUniqueId()))
                    p.sendMessage(Component.text("/pass premium — Upgrade fuer "
                        + HardcorePlugin.dollar(plugin.getConfig().getDouble("pass-premium-kosten", 5000.0)), NamedTextColor.YELLOW));
            }
            case "statistik" -> {
                OfflinePlayer t = args.length >= 1 ? Bukkit.getOfflinePlayer(args[0]) : p;
                var id = t.getUniqueId();
                var guild = plugin.guilds().byMember(id);
                p.sendMessage(Component.text("── Statistik: " + t.getName() + " ──", NamedTextColor.GOLD));
                p.sendMessage(Component.text("Geld: " + HardcorePlugin.dollar(plugin.economy().get(id))
                    + " | Kills: " + plugin.stats().kills().getOrDefault(id, 0), NamedTextColor.GRAY));
                p.sendMessage(Component.text("Gilde: " + (guild != null ? guild.name : "-")
                    + " | Season-Level: " + plugin.extras().level(id), NamedTextColor.GRAY));
                p.sendMessage(Component.text("Kopfgeld auf ihm: " + HardcorePlugin.dollar(plugin.bounties().total(id))
                    + " | Joker: " + (plugin.bans().hasJoker(id) ? "verfuegbar" : "verbraucht"), NamedTextColor.GRAY));
                double debt = plugin.extras().debt(id);
                if (debt > 0) p.sendMessage(Component.text("Blutgeld-Schulden: " + HardcorePlugin.dollar(debt), NamedTextColor.RED));
            }
            case "zone" -> {
                if (!p.hasPermission("doofie.invsee")) {
                    p.sendMessage(Component.text("Nur fuer Admins!", NamedTextColor.RED));
                    return true;
                }
                int radius = 30;
                if (args.length >= 1) try { radius = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
                ex.addZone(new SmpExtras.Zone(p.getWorld().getName(), p.getLocation().getBlockX(), p.getLocation().getBlockZ(), radius));
                Bukkit.broadcast(Component.text("FRIEDENSZONE! Um " + p.getLocation().getBlockX() + "/"
                    + p.getLocation().getBlockZ() + " (Radius " + radius + ") ist PvP jetzt verboten.", NamedTextColor.AQUA));
            }
        }
        return true;
    }
}
