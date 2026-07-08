package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.Random;

/** Dynamische Weltevents: Blutmond, Goldrausch, Saeuberung, Haendlerpause. */
public class EventManager {

    public enum EventType {
        NONE("Kein Event", ""),
        BLUTMOND("BLUTMOND", "Alle Kopfgelder zaehlen DOPPELT!"),
        GOLDRAUSCH("GOLDRAUSCH", "Doppelte Verkaufspreise bei /sell!"),
        SAEUBERUNG("DIE SAEUBERUNG", "JEDER Spieler-Kill bringt 200$!"),
        HAENDLERPAUSE("HAENDLER-STREIK", "Das /ah ist geschlossen!");

        public final String title;
        public final String desc;
        EventType(String title, String desc) { this.title = title; this.desc = desc; }
    }

    private final HardcorePlugin plugin;
    private final Random random = new Random();
    private EventType current = EventType.NONE;
    private long until = 0;

    public EventManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        // Jede Minute pruefen: Event vorbei? Neues Event starten?
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L * 60, 20L * 60);
    }

    private void tick() {
        if (current != EventType.NONE && System.currentTimeMillis() > until) {
            Bukkit.broadcast(Component.text("EVENT VORBEI: " + current.title, NamedTextColor.GRAY));
            current = EventType.NONE;
        }
        if (current == EventType.NONE && random.nextInt(100) < 3) { // ~alle 30-40 Minuten
            EventType[] pool = { EventType.BLUTMOND, EventType.GOLDRAUSCH, EventType.SAEUBERUNG, EventType.HAENDLERPAUSE };
            start(pool[random.nextInt(pool.length)], 15 + random.nextInt(16));
        }
    }

    public void start(EventType type, int minutes) {
        current = type;
        until = System.currentTimeMillis() + minutes * 60_000L;
        NamedTextColor color = type == EventType.BLUTMOND || type == EventType.SAEUBERUNG
            ? NamedTextColor.DARK_RED : NamedTextColor.GOLD;
        Title title = Title.title(
            Component.text(type.title, color),
            Component.text(type.desc + " (" + minutes + " Min.)", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofSeconds(1)));
        Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));
        Bukkit.broadcast(Component.text("EVENT: " + type.title + " — " + type.desc, color));
    }

    public boolean isActive(EventType type) {
        return current == type && System.currentTimeMillis() <= until;
    }

    public EventType current() {
        return System.currentTimeMillis() <= until ? current : EventType.NONE;
    }

    public long remainingMinutes() {
        return Math.max(0, (until - System.currentTimeMillis()) / 60_000L);
    }

    public double sellMultiplier() { return isActive(EventType.GOLDRAUSCH) ? 2.0 : 1.0; }
    public double bountyMultiplier() { return isActive(EventType.BLUTMOND) ? 2.0 : 1.0; }
}
