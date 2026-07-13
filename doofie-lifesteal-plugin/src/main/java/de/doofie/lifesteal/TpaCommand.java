package de.doofie.lifesteal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Das Doofie-TPA-System (Lifesteal-Ausgabe):
 *   /tpa <spieler>     — Anfrage: ICH will zu DIR
 *   /tpahere <spieler> — Anfrage: DU sollst zu MIR
 *   /tpaccept          — letzte Anfrage annehmen (auch: /tpa annehmen)
 *   /tpadeny           — letzte Anfrage ablehnen  (auch: /tpa ablehnen)
 *   /tpaauto           — Auto-Annehmen ein-/ausschalten
 * Eliminierte (Zuschauer) koennen weder anfragen noch angefragt werden.
 * Nach dem Annehmen: 3 Sekunden Warmup (nicht bewegen!), dann Teleport.
 * Alle Befehle haben Tab-Vervollstaendigung (Spielernamen + Unterbefehle).
 */
public class TpaCommand implements TabExecutor {

    /** here = true: der ZIEL-Spieler soll sich zum Anfragenden bewegen. */
    private record Request(UUID sender, long time, boolean here) {}

    private final LifestealPlugin plugin;
    /** Ziel -> letzte Anfrage */
    private final Map<UUID, Request> requests = new HashMap<>();
    /** Spieler mit aktivem Auto-Annehmen */
    private final Set<UUID> autoAccept = new HashSet<>();

    public TpaCommand(LifestealPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (String c : new String[]{"tpa", "tpahere", "tpaccept", "tpadeny", "tpaauto"}) {
            plugin.getCommand(c).setExecutor(this);
            plugin.getCommand(c).setTabCompleter(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }

        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "tpaccept" -> { accept(player); return true; }
            case "tpadeny" -> { deny(player); return true; }
            case "tpaauto" -> { toggleAuto(player); return true; }
            case "tpahere" -> {
                if (args.length < 1) {
                    player.sendMessage(Component.text("Nutzung: /tpahere <spieler>", NamedTextColor.RED));
                    return true;
                }
                sendRequest(player, args[0], true);
                return true;
            }
            default -> { /* /tpa faellt durch */ }
        }

        if (args.length < 1) {
            player.sendMessage(Component.text(
                "Nutzung: /tpa <spieler> — /tpahere <spieler> — /tpaccept — /tpadeny — /tpaauto",
                NamedTextColor.RED));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("annehmen") || sub.equals("accept")) { accept(player); return true; }
        if (sub.equals("ablehnen") || sub.equals("deny")) { deny(player); return true; }

        sendRequest(player, args[0], false);
        return true;
    }

    // ────────────────────────── Aktionen ──────────────────────────

    private void sendRequest(Player player, String zielName, boolean here) {
        Player target = Bukkit.getPlayerExact(zielName);
        if (target == null || target.equals(player)) {
            player.sendMessage(Component.text("Spieler nicht gefunden.", NamedTextColor.RED));
            return;
        }
        if (plugin.getHeartManager().istEliminiert(player.getUniqueId())
            || plugin.getHeartManager().istEliminiert(target.getUniqueId())) {
            player.sendMessage(Component.text("Eliminierte Zuschauer koennen kein TPA nutzen.", NamedTextColor.RED));
            return;
        }
        requests.put(target.getUniqueId(), new Request(player.getUniqueId(), System.currentTimeMillis(), here));

        // Auto-Annehmen: Ziel hat /tpaauto an — sofort los
        if (autoAccept.contains(target.getUniqueId())) {
            target.sendMessage(Component.text("TPA von " + player.getName()
                + " automatisch angenommen (/tpaauto).", NamedTextColor.AQUA));
            accept(target);
            return;
        }

        player.sendMessage(Component.text("Teleport-Anfrage an " + target.getName() + " gesendet (60s gueltig).", NamedTextColor.GREEN));
        target.sendMessage(Component.text()
            .append(Component.text("TPA! ", NamedTextColor.AQUA))
            .append(Component.text(here
                ? player.getName() + " moechte dich zu sich teleportieren. "
                : player.getName() + " moechte sich zu dir teleportieren. ", NamedTextColor.GRAY))
            .append(Component.text("/tpaccept", NamedTextColor.GREEN))
            .append(Component.text(" oder ", NamedTextColor.GRAY))
            .append(Component.text("/tpadeny", NamedTextColor.RED))
            .build());
    }

    private void accept(Player player) {
        Request req = pending(player.getUniqueId());
        if (req == null) {
            player.sendMessage(Component.text("Keine offene Teleport-Anfrage.", NamedTextColor.RED));
            return;
        }
        requests.remove(player.getUniqueId());
        Player requester = Bukkit.getPlayer(req.sender());
        if (requester == null) {
            player.sendMessage(Component.text("Der Anfragende ist nicht mehr online.", NamedTextColor.RED));
            return;
        }
        // /tpa: der Anfragende bewegt sich zum Ziel — /tpahere: umgekehrt
        if (req.here()) {
            startWarmup(player, requester);
        } else {
            startWarmup(requester, player);
        }
    }

    private void deny(Player player) {
        Request req = pending(player.getUniqueId());
        requests.remove(player.getUniqueId());
        if (req == null) {
            player.sendMessage(Component.text("Keine offene Teleport-Anfrage.", NamedTextColor.RED));
            return;
        }
        Player requester = Bukkit.getPlayer(req.sender());
        if (requester != null) {
            requester.sendMessage(Component.text(player.getName() + " hat deine Teleport-Anfrage abgelehnt.", NamedTextColor.RED));
        }
        player.sendMessage(Component.text("Anfrage abgelehnt.", NamedTextColor.GRAY));
    }

    private void toggleAuto(Player player) {
        if (autoAccept.remove(player.getUniqueId())) {
            player.sendMessage(Component.text("Auto-Annehmen AUS — Anfragen brauchen wieder /tpaccept.", NamedTextColor.GRAY));
        } else {
            autoAccept.add(player.getUniqueId());
            player.sendMessage(Component.text("Auto-Annehmen AN — jede TPA-Anfrage wird sofort angenommen!", NamedTextColor.GREEN));
        }
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

    // ────────────────────────── Tab-Vorschlaege ──────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("tpaccept") || name.equals("tpadeny") || name.equals("tpaauto")) return List.of();
        if (args.length != 1) return List.of();

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> vorschlaege = new java.util.ArrayList<>();
        if (name.equals("tpa")) {
            for (String s : List.of("annehmen", "ablehnen")) {
                if (s.startsWith(prefix)) vorschlaege.add(s);
            }
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(sender) && p.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                vorschlaege.add(p.getName());
            }
        }
        return vorschlaege;
    }
}
