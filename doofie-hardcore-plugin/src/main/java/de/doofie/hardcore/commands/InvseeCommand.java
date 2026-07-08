package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/**
 * /invsee <spieler> [ender] — Admin-Einblick in fremde Inventare.
 * Nur fuer OPs bzw. Spieler mit der Permission doofie.invsee.
 * Das Inventar ist LIVE: Admins koennen Items rein-/rausnehmen.
 */
public class InvseeCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public InvseeCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player admin)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        if (!admin.hasPermission("doofie.invsee")) {
            admin.sendMessage(Component.text("Dafuer hast du keine Rechte!", NamedTextColor.RED));
            return true;
        }
        if (args.length < 1) {
            admin.sendMessage(Component.text("Nutzung: /invsee <spieler> [ender]", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            admin.sendMessage(Component.text("Spieler nicht online.", NamedTextColor.RED));
            return true;
        }
        if (target.equals(admin)) {
            admin.sendMessage(Component.text("Dein eigenes Inventar kennst du hoffentlich.", NamedTextColor.GRAY));
            return true;
        }

        boolean ender = args.length >= 2 && args[1].toLowerCase(Locale.ROOT).startsWith("ender");
        if (ender) {
            admin.openInventory(target.getEnderChest());
            admin.sendMessage(Component.text("Enderkiste von " + target.getName() + " geoeffnet.", NamedTextColor.GREEN));
        } else {
            admin.openInventory(target.getInventory());
            admin.sendMessage(Component.text("Inventar von " + target.getName()
                + " geoeffnet (live — Aenderungen wirken sofort).", NamedTextColor.GREEN));
        }
        return true;
    }
}
