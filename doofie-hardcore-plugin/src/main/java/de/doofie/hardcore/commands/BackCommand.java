package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * /back — einmaliger Teleport zur letzten Todesposition, gegen Gebuehr.
 */
public class BackCommand implements CommandExecutor, Listener {

    private final HardcorePlugin plugin;
    private final Map<UUID, Location> deaths = new HashMap<>();

    public BackCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player p = event.getEntity();
        deaths.put(p.getUniqueId(), p.getLocation());
        double price = plugin.getConfig().getDouble("back-preis", 250);
        p.sendMessage(Component.text("Mit /back kommst du fuer " + HardcorePlugin.dollar(price)
                + " zu deiner Todesstelle zurueck (einmalig).", NamedTextColor.GRAY));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        Location death = deaths.get(p.getUniqueId());
        if (death == null) {
            p.sendMessage(Component.text("Keine Todesstelle gespeichert.", NamedTextColor.RED));
            return true;
        }
        if (death.getWorld() == null) {
            deaths.remove(p.getUniqueId());
            p.sendMessage(Component.text("Die Welt deiner Todesstelle existiert nicht mehr.", NamedTextColor.RED));
            return true;
        }
        double price = plugin.getConfig().getDouble("back-preis", 250);
        if (!plugin.economy().withdraw(p.getUniqueId(), price)) {
            p.sendMessage(Component.text("Zu wenig Geld — /back kostet " + HardcorePlugin.dollar(price), NamedTextColor.RED));
            return true;
        }
        deaths.remove(p.getUniqueId());
        p.teleport(death);
        p.sendMessage(Component.text("Zurueck an deiner Todesstelle. Viel Glueck beim Einsammeln!", NamedTextColor.GREEN));
        return true;
    }
}
