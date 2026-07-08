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

/** /testament <spieler> — Erbe festlegen (greift nach 7 Tagen Bann). */
public class TestamentCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public TestamentCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        if (args.length < 1) {
            var heir = plugin.testament().heirOf(p.getUniqueId());
            String name = heir != null ? Bukkit.getOfflinePlayer(heir).getName() : null;
            p.sendMessage(Component.text(name != null
                ? "Dein Erbe: " + name + " — /testament <spieler> aendert es"
                : "Kein Testament. /testament <spieler> — der erbt dein halbes Vermoegen, wenn du 7 Tage gebannt bleibst.", NamedTextColor.GRAY));
            return true;
        }
        OfflinePlayer heir = Bukkit.getOfflinePlayer(args[0]);
        if (heir.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage(Component.text("Du kannst dich nicht selbst beerben, Doofie.", NamedTextColor.RED));
            return true;
        }
        plugin.testament().setHeir(p.getUniqueId(), heir.getUniqueId());
        p.sendMessage(Component.text("Testament geschrieben: " + args[0]
            + " erbt dein halbes Vermoegen, falls du 7 Tage gebannt bleibst. Die andere Haelfte wandert in den Lotto-Pot.", NamedTextColor.GOLD));
        return true;
    }
}
