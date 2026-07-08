package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * /gerichtsduell — Gebannter fordert seinen Killer (nur EINMAL!)
 * /gerichtsduell annehmen — Killer nimmt an. Sieg des Gebannten = frei,
 * Niederlage = Freikauf verdoppelt sich.
 */
public class GerichtsduellCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public GerichtsduellCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }

        if (args.length >= 1 && args[0].equalsIgnoreCase("annehmen")) {
            for (var entry : plugin.bans().all().entrySet()) {
                var ban = entry.getValue();
                if (ban.killer == null || !ban.killer.equals(p.getUniqueId())) continue;
                if (plugin.duels().courtOpponent(entry.getKey()) == null) continue;
                Player accused = Bukkit.getPlayer(entry.getKey());
                if (accused == null) continue;
                accused.setGameMode(GameMode.SURVIVAL);
                accused.teleport(p.getLocation());
                Bukkit.broadcast(Component.text()
                    .append(Component.text("GERICHTSDUELL! ", NamedTextColor.DARK_RED))
                    .append(Component.text(accused.getName() + " kaempft gegen seinen Killer " + p.getName()
                        + " um die FREIHEIT!", NamedTextColor.GOLD))
                    .build());
                return true;
            }
            p.sendMessage(Component.text("Niemand hat dich zum Gerichtsduell gefordert.", NamedTextColor.RED));
            return true;
        }

        var ban = plugin.bans().get(p.getUniqueId());
        if (ban == null) { p.sendMessage(Component.text("Du bist nicht gebannt.", NamedTextColor.RED)); return true; }
        if (ban.courtUsed) { p.sendMessage(Component.text("Du hattest deine eine Chance schon!", NamedTextColor.RED)); return true; }
        if (ban.killer == null) { p.sendMessage(Component.text("Dein Killer ist unbekannt.", NamedTextColor.RED)); return true; }
        Player killer = Bukkit.getPlayer(ban.killer);
        if (killer == null) { p.sendMessage(Component.text("Dein Killer ist nicht online — versuch es spaeter.", NamedTextColor.RED)); return true; }

        ban.courtUsed = true;
        plugin.duels().startCourtDuel(p.getUniqueId(), ban.killer);
        p.sendMessage(Component.text("Forderung gestellt! Nimmt " + killer.getName()
            + " an, kaempfst du um deine Freiheit — verlierst du, verdoppelt sich dein Freikauf!", NamedTextColor.GOLD));
        killer.sendMessage(Component.text()
            .append(Component.text("GERICHTSDUELL! ", NamedTextColor.DARK_RED))
            .append(Component.text(p.getName() + " fordert dich zum Kampf um seine Freiheit. ", NamedTextColor.GRAY))
            .append(Component.text("/gerichtsduell annehmen", NamedTextColor.GOLD))
            .build());
        return true;
    }
}
