package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Dynamische Weltevents: Blutmond, Goldrausch, Saeuberung, Haendlerpause. */
public class EventManager {

    public enum EventType {
        NONE("Kein Event", ""),
        BLUTMOND("BLUTMOND", "Alle Kopfgelder zaehlen DOPPELT!"),
        GOLDRAUSCH("GOLDRAUSCH", "Doppelte Verkaufspreise bei /sell!"),
        SAEUBERUNG("DIE SAEUBERUNG", "JEDER Spieler-Kill bringt 200$!"),
        HAENDLERPAUSE("HAENDLER-STREIK", "Das /ah ist geschlossen!"),
        TRESOR("TRESOR-RAUB", "Halte den Tresor 2 Minuten und knacke den Steuer-Topf!");

        public final String title;
        public final String desc;
        EventType(String title, String desc) { this.title = title; this.desc = desc; }
    }

    private final HardcorePlugin plugin;
    private final Random random = new Random();
    private EventType current = EventType.NONE;
    private long until = 0;
    private Location tresor = null;
    private final Map<UUID, Integer> holdTicks = new HashMap<>();

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
            // Tresor-Raub nur, wenn genug Steuern im Topf sind
            if (plugin.extras().taxPot() >= 500 && random.nextInt(3) == 0) {
                startTresor();
            } else {
                EventType[] pool = { EventType.BLUTMOND, EventType.GOLDRAUSCH, EventType.SAEUBERUNG, EventType.HAENDLERPAUSE };
                start(pool[random.nextInt(pool.length)], 15 + random.nextInt(16));
            }
        }
    }

    private void startTresor() {
        var world = Bukkit.getWorlds().get(0);
        var spawn = world.getSpawnLocation();
        int x = spawn.getBlockX() + random.nextInt(1000) - 500;
        int z = spawn.getBlockZ() + random.nextInt(1000) - 500;
        int y = world.getHighestBlockYAt(x, z) + 1;
        tresor = new Location(world, x, y, z);
        world.getBlockAt(x, y - 1, z).setType(org.bukkit.Material.GOLD_BLOCK);
        holdTicks.clear();
        start(EventType.TRESOR, 15);
        Bukkit.broadcast(Component.text("TRESOR bei " + x + " / " + y + " / " + z + " — Pot: "
            + de.doofie.hardcore.HardcorePlugin.dollar(plugin.extras().taxPot()), NamedTextColor.GOLD));

        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isActive(EventType.TRESOR) || tresor == null) return;
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(tresor.getWorld())) continue;
                if (p.getLocation().distanceSquared(tresor) > 25) continue;
                int t = holdTicks.merge(p.getUniqueId(), 1, Integer::sum);
                if (t % 6 == 0) p.sendMessage(Component.text("Tresor: " + (t * 5) + "/120 Sekunden...", NamedTextColor.GOLD));
                if (t >= 24) { // 24 x 5s = 2 Minuten
                    double pot = plugin.extras().drainTaxPot();
                    plugin.economy().deposit(p.getUniqueId(), pot);
                    Bukkit.broadcast(Component.text("TRESOR GEKNACKT! " + p.getName() + " raubt "
                        + de.doofie.hardcore.HardcorePlugin.dollar(pot) + "!", NamedTextColor.GOLD));
                    tresor.getBlock().getRelative(0, -1, 0).setType(org.bukkit.Material.AIR);
                    tresor = null;
                    current = EventType.NONE;
                    return;
                }
            }
        }, 100L, 100L);
        Bukkit.getScheduler().runTaskLater(plugin, task::cancel, 20L * 60 * 16);
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
