package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.GuildManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Locale;

/** /gilde gruenden|einladen|beitreten|verlassen|kasse|krieg|kriegannehmen|chat|liste|info */
public class GildeCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public GildeCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        var gm = plugin.guilds();
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        GuildManager.Guild own = gm.byMember(p.getUniqueId());

        switch (sub) {
            case "gruenden", "create" -> {
                if (own != null) { msg(p, "Du bist schon in einer Gilde.", NamedTextColor.RED); return true; }
                if (args.length < 2) { msg(p, "Nutzung: /gilde gruenden <name>", NamedTextColor.RED); return true; }
                String name = args[1];
                if (name.length() > 16 || gm.byName(name) != null) { msg(p, "Name vergeben oder zu lang.", NamedTextColor.RED); return true; }
                double cost = plugin.getConfig().getDouble("gilde-kosten", 5000.0);
                if (!plugin.economy().withdraw(p.getUniqueId(), cost)) { msg(p, "Gilde gruenden kostet " + HardcorePlugin.dollar(cost), NamedTextColor.RED); return true; }
                gm.create(name, p.getUniqueId());
                Bukkit.broadcast(Component.text("Neue Gilde: " + name + " (Anfuehrer: " + p.getName() + ")", NamedTextColor.GOLD));
            }
            case "einladen", "invite" -> {
                if (own == null || !own.leader.equals(p.getUniqueId())) { msg(p, "Nur der Anfuehrer laedt ein.", NamedTextColor.RED); return true; }
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (t == null) { msg(p, "Spieler nicht online.", NamedTextColor.RED); return true; }
                gm.invite(t.getUniqueId(), own.name);
                msg(t, p.getName() + " laedt dich in die Gilde " + own.name + " ein — /gilde beitreten", NamedTextColor.GOLD);
                msg(p, "Einladung gesendet.", NamedTextColor.GREEN);
            }
            case "beitreten", "join" -> {
                if (own != null) { msg(p, "Du bist schon in einer Gilde.", NamedTextColor.RED); return true; }
                String inv = gm.inviteOf(p.getUniqueId());
                var g = inv != null ? gm.byName(inv) : null;
                if (g == null) { msg(p, "Keine offene Einladung.", NamedTextColor.RED); return true; }
                g.members.add(p.getUniqueId());
                Bukkit.broadcast(Component.text(p.getName() + " ist der Gilde " + g.name + " beigetreten!", NamedTextColor.GOLD));
            }
            case "verlassen", "leave" -> {
                if (own == null) { msg(p, "Du bist in keiner Gilde.", NamedTextColor.RED); return true; }
                own.members.remove(p.getUniqueId());
                if (own.leader.equals(p.getUniqueId()) || own.members.isEmpty()) {
                    gm.disband(own);
                    Bukkit.broadcast(Component.text("Die Gilde " + own.name + " wurde aufgeloest!", NamedTextColor.RED));
                } else {
                    msg(p, "Gilde verlassen.", NamedTextColor.YELLOW);
                }
            }
            case "kasse", "bank" -> {
                if (own == null) { msg(p, "Du bist in keiner Gilde.", NamedTextColor.RED); return true; }
                if (args.length < 2) { msg(p, "Kasse: " + HardcorePlugin.dollar(own.bank) + " — /gilde kasse <betrag> zahlt ein", NamedTextColor.GOLD); return true; }
                double amount;
                try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException e) { return true; }
                if (amount <= 0 || !plugin.economy().withdraw(p.getUniqueId(), amount)) { msg(p, "Nicht genug Geld!", NamedTextColor.RED); return true; }
                own.bank += amount;
                msg(p, HardcorePlugin.dollar(amount) + " eingezahlt. Kasse: " + HardcorePlugin.dollar(own.bank), NamedTextColor.GREEN);
            }
            case "krieg", "war" -> {
                if (own == null || !own.leader.equals(p.getUniqueId())) { msg(p, "Nur der Anfuehrer erklaert Krieg.", NamedTextColor.RED); return true; }
                if (gm.war() != null) { msg(p, "Es laeuft schon ein Krieg!", NamedTextColor.RED); return true; }
                if (args.length < 3) { msg(p, "Nutzung: /gilde krieg <gilde> <einsatz>", NamedTextColor.RED); return true; }
                var enemy = gm.byName(args[1]);
                if (enemy == null || enemy == own) { msg(p, "Gilde nicht gefunden.", NamedTextColor.RED); return true; }
                double stake;
                try { stake = Double.parseDouble(args[2]); } catch (NumberFormatException e) { return true; }
                if (stake <= 0 || own.bank < stake) { msg(p, "Nicht genug in der Gildenkasse!", NamedTextColor.RED); return true; }
                gm.requestWar(own.name, enemy.name, stake);
                Bukkit.broadcast(Component.text("KRIEGSERKLAERUNG! " + own.name + " fordert " + enemy.name
                    + " um " + HardcorePlugin.dollar(stake) + " — Anfuehrer: /gilde kriegannehmen", NamedTextColor.DARK_RED));
            }
            case "kriegannehmen" -> {
                if (own == null || !own.leader.equals(p.getUniqueId())) { msg(p, "Nur der Anfuehrer.", NamedTextColor.RED); return true; }
                String[] req = gm.warRequestFor(own.name);
                if (req == null) { msg(p, "Keine Kriegserklaerung offen.", NamedTextColor.RED); return true; }
                var attacker = gm.byName(req[0]);
                double stake = Double.parseDouble(req[1]);
                if (attacker == null || attacker.bank < stake || own.bank < stake) { msg(p, "Eine Kasse hat den Einsatz nicht mehr.", NamedTextColor.RED); return true; }
                attacker.bank -= stake;
                own.bank -= stake;
                gm.startWar(attacker.name, own.name, stake * 2);
                Bukkit.broadcast(Component.text("KRIEG! " + attacker.name + " vs " + own.name + " um "
                    + HardcorePlugin.dollar(stake * 2) + " — erste Gilde mit " + GuildManager.WAR_KILL_TARGET + " Kills gewinnt!", NamedTextColor.DARK_RED));
            }
            case "chat", "c" -> {
                if (own == null || args.length < 2) { msg(p, "Nutzung: /gilde chat <nachricht>", NamedTextColor.RED); return true; }
                String text = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                for (var m : own.members) {
                    Player mp = Bukkit.getPlayer(m);
                    if (mp != null) mp.sendMessage(Component.text("[" + own.name + "] " + p.getName() + ": " + text, NamedTextColor.AQUA));
                }
            }
            case "liste", "list" -> {
                msg(p, "── Gilden ──", NamedTextColor.GOLD);
                gm.all().values().forEach(g -> msg(p, "  " + g.name + " — " + g.members.size()
                    + " Mitglieder, Kasse " + HardcorePlugin.dollar(g.bank), NamedTextColor.YELLOW));
            }
            default -> {
                if (own == null) { msg(p, "Du bist in keiner Gilde. /gilde gruenden <name> ("
                    + HardcorePlugin.dollar(plugin.getConfig().getDouble("gilde-kosten", 5000.0)) + ")", NamedTextColor.GRAY); return true; }
                msg(p, "Gilde: " + own.name + " | Mitglieder: " + own.members.size() + " | Kasse: " + HardcorePlugin.dollar(own.bank), NamedTextColor.GOLD);
            }
        }
        return true;
    }

    private void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(Component.text(text, color));
    }
}
