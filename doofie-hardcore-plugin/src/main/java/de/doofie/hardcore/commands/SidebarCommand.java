package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /sidebar — Stats-Anzeige ein-/ausblenden. */
public class SidebarCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public SidebarCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        boolean visible = plugin.sidebar().toggle(p.getUniqueId());
        p.sendMessage(Component.text(visible ? "Sidebar eingeblendet." : "Sidebar ausgeblendet.", NamedTextColor.GREEN));
        return true;
    }
}
