package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /freikaufen — nach normalem Tod: GRATIS zurueck ins Leben.
 * Nach Kopfgeld-Bann: /freikaufen joker (1x im Leben gratis)
 * oder /freikaufen zahlen (Kopfgeld +5%).
 * /freikaufen <spieler> — einen Freund freikaufen (bezahlt).
 */
public class FreikaufCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public FreikaufCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player payer)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        String arg = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";

        // ── Normaler Tod: gratis wiederbeleben ──
        if (plugin.bans().isDead(payer.getUniqueId())) {
            plugin.bans().revive(payer.getUniqueId());
            revive(payer);
            payer.sendMessage(Component.text("Willkommen zurueck im Leben!", NamedTextColor.GREEN));
            return true;
        }

        // ── Kopfgeld-Bann: Selbst-Freikauf mit Joker-Wahl ──
        if (plugin.bans().isBanned(payer.getUniqueId()) && (arg.isEmpty() || arg.equals("joker") || arg.equals("zahlen") || arg.equals("pump"))) {
            double cost = plugin.bans().unbanCost(payer.getUniqueId());
            boolean joker = plugin.bans().hasJoker(payer.getUniqueId());

            if (arg.equals("joker")) {
                if (!joker) {
                    payer.sendMessage(Component.text("Dein Joker ist schon verbraucht!", NamedTextColor.RED));
                    return true;
                }
                plugin.bans().useJoker(payer.getUniqueId());
                plugin.bans().unban(payer.getUniqueId());
                revive(payer);
                Bukkit.broadcast(Component.text(payer.getName()
                    + " hat seinen GRATIS-JOKER eingeloest — ab jetzt wird es teuer!", NamedTextColor.LIGHT_PURPLE));
                return true;
            }
            if (arg.equals("pump")) {
                plugin.extras().addDebt(payer.getUniqueId(), cost);
                plugin.bans().unban(payer.getUniqueId());
                revive(payer);
                Bukkit.broadcast(Component.text(payer.getName() + " hat sich auf PUMP freigekauft — "
                    + HardcorePlugin.dollar(cost) + " Blutgeld-Schulden (+10% Zinsen pro Tag)!", NamedTextColor.RED));
                return true;
            }
            if (arg.equals("zahlen")) {
                if (!plugin.economy().withdraw(payer.getUniqueId(), cost)) {
                    payer.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(cost)
                        + ") — oder /freikaufen pump (Schulden mit 10%/Tag Zinsen)", NamedTextColor.RED));
                    return true;
                }
                plugin.extras().addTax(cost);
                plugin.bans().unban(payer.getUniqueId());
                revive(payer);
                Bukkit.broadcast(Component.text(payer.getName() + " hat sich fuer "
                    + HardcorePlugin.dollar(cost) + " freigekauft!", NamedTextColor.GREEN));
                return true;
            }
            // Optionen anzeigen
            payer.sendMessage(Component.text("── Deine Freikauf-Optionen ──", NamedTextColor.GOLD));
            if (joker) payer.sendMessage(Component.text("  /freikaufen joker — dein EINMALIGER Gratis-Joker", NamedTextColor.LIGHT_PURPLE));
            payer.sendMessage(Component.text("  /freikaufen zahlen — kostet " + HardcorePlugin.dollar(cost), NamedTextColor.YELLOW));
            payer.sendMessage(Component.text("  /freikaufen pump — auf Schulden (10%/Tag Zinsen, Haelfte aller Einnahmen tilgt)", NamedTextColor.RED));
            payer.sendMessage(Component.text("  Oder ein Freund zahlt: /freikaufen " + payer.getName(), NamedTextColor.GRAY));
            return true;
        }

        // ── Einen anderen Spieler freikaufen (immer bezahlt) ──
        if (!arg.isEmpty()) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!plugin.bans().isBanned(target.getUniqueId())) {
                payer.sendMessage(Component.text(args[0] + " ist nicht gebannt.", NamedTextColor.RED));
                return true;
            }
            double cost = plugin.bans().unbanCost(target.getUniqueId());
            if (!plugin.economy().withdraw(payer.getUniqueId(), cost)) {
                payer.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(cost) + ")", NamedTextColor.RED));
                return true;
            }
            plugin.bans().unban(target.getUniqueId());
            Player online = target.getPlayer();
            if (online != null) revive(online);
            Bukkit.broadcast(Component.text()
                .append(Component.text("FREIGEKAUFT! ", NamedTextColor.GREEN))
                .append(Component.text(payer.getName() + " hat " + target.getName() + " fuer "
                    + HardcorePlugin.dollar(cost) + " freigekauft!", NamedTextColor.GRAY))
                .build());
            return true;
        }

        payer.sendMessage(Component.text("Du bist weder tot noch gebannt.", NamedTextColor.GRAY));
        return true;
    }

    /** Zurueck ins Leben: Survival am Bett-Respawnpunkt oder Welt-Spawn. */
    private void revive(Player player) {
        player.setGameMode(GameMode.SURVIVAL);
        var respawn = player.getRespawnLocation();
        player.teleport(respawn != null ? respawn : player.getWorld().getSpawnLocation());
        player.sendMessage(Component.text("Du bist frei!", NamedTextColor.GREEN));
    }
}
