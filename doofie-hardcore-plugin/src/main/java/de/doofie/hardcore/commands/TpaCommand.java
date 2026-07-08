package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /tpa <spieler> — Teleport-Anfrage senden
 * /tpa annehmen | ablehnen — Anfrage beantworten
 * Nach dem Annehmen: 3 Sekunden Warmup (nicht bewegen!), dann Teleport.
 */
public class TpaCommand implements CommandExecutor {

    private record Request(UUID sender, long time) {}

    private final HardcorePlugin plugin;
    /** Ziel -> letzte Anfrage */
    private final Map<UUID, Request> requests = new HashMap<>();

    public TpaCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage(Component.text("Nutzung: /tpa <spieler> — oder /tpa annehmen | ablehnen", NamedTextColor.RED));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("annehmen") || sub.equals("accept")) {
            Request req = pending(player.getUniqueId());
            if (req == null) {
                player.sendMessage(Component.text("Keine offene Teleport-Anfrage.", NamedTextColor.RED));
                return true;
            }
            requests.remove(player.getUniqueId());
            Player requester = Bukkit.getPlayer(req.sender());
            if (requester == null) {
                player.sendMessage(Component.text("Der Anfragende ist nicht mehr online.", NamedTextColor.RED));
                return true;
            }
            startWarmup(requester, player);
            return true;
        }

        if (sub.equals("ablehnen") || sub.equals("deny")) {
            Request req = pending(player.getUniqueId());
            requests.remove(player.getUniqueId());
            if (req == null) {
                player.sendMessage(Component.text("Keine offene Teleport-Anfrage.", NamedTextColor.RED));
                return true;
            }
            Player requester = Bukkit.getPlayer(req.sender());
            if (requester != null) {
                requester.sendMessage(Component.text(player.getName() + " hat deine Teleport-Anfrage abgelehnt.", NamedTextColor.RED));
            }
            player.sendMessage(Component.text("Anfrage abgelehnt.", NamedTextColor.GRAY));
            return true;
        }

        // ── Anfrage senden ──
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            player.sendMessage(Component.text("Spieler nicht gefunden.", NamedTextColor.RED));
            return true;
        }
        if (plugin.bans().isBanned(player.getUniqueId())) {
            player.sendMessage(Component.text("Gebannt — kein Teleport fuer dich!", NamedTextColor.RED));
            return true;
        }
        requests.put(target.getUniqueId(), new Request(player.getUniqueId(), System.currentTimeMillis()));
        player.sendMessage(Component.text("Teleport-Anfrage an " + target.getName() + " gesendet (60s gueltig).", NamedTextColor.GREEN));
        target.sendMessage(Component.text()
            .append(Component.text("TPA! ", NamedTextColor.AQUA))
            .append(Component.text(player.getName() + " moechte sich zu dir teleportieren. ", NamedTextColor.GRAY))
            .append(Component.text("/tpa annehmen", NamedTextColor.GREEN))
            .append(Component.text(" oder ", NamedTextColor.GRAY))
            .append(Component.text("/tpa ablehnen", NamedTextColor.RED))
            .build());
        return true;
    }

    private Request pending(UUID target) {
        Request r = requests.get(target);
        if (r == null) return null;
        if (System.currentTimeMillis() - r.time() > 60_000L) {
            requests.remove(target);
            return null;
        }
        return r;
    }

    private void startWarmup(Player mover, Player target) {
        Location startLoc = mover.getLocation().clone();
        mover.sendMessage(Component.text("Teleport in 3 Sekunden — nicht bewegen!", NamedTextColor.AQUA));
        target.sendMessage(Component.text(mover.getName() + " teleportiert sich gleich zu dir.", NamedTextColor.GRAY));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!mover.isOnline() || !target.isOnline()) return;
            if (mover.getLocation().distanceSquared(startLoc) > 1.0) {
                mover.sendMessage(Component.text("Teleport abgebrochen — du hast dich bewegt!", NamedTextColor.RED));
                return;
            }
            mover.teleport(target.getLocation());
            mover.sendMessage(Component.text("Teleportiert zu " + target.getName() + "!", NamedTextColor.GREEN));
        }, 60L);
    }
}
