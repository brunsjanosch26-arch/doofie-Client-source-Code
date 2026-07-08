package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.EventManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/** /events — aktuelles Weltevent anzeigen. */
public class EventsCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public EventsCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        var ev = plugin.events().current();
        if (ev == EventManager.EventType.NONE) {
            sender.sendMessage(Component.text("Kein Event aktiv — Weltevents starten zufaellig. Bleib wachsam!", NamedTextColor.GRAY));
        } else {
            sender.sendMessage(Component.text("AKTIV: " + ev.title + " — " + ev.desc
                + " (noch " + plugin.events().remainingMinutes() + " Min.)", NamedTextColor.GOLD));
        }
        return true;
    }
}
