package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Kopfgeld-Bann: Freikauf-Betrag, Killer (fuer Gerichtsduell), Bann-Zeitpunkt.
 */
public class BanManager {

    public static class Ban {
        public double cost;
        public UUID killer;
        public long time;
        public boolean courtUsed;
    }

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, Ban> banned = new HashMap<>();
    /** Normal Gestorbene, die auf ihren Gratis-Respawn warten. */
    private final Set<UUID> dead = new HashSet<>();
    /** Spieler, die ihren einmaligen Gratis-Joker schon verbraucht haben. */
    private final Set<UUID> jokerUsed = new HashSet<>();

    public BanManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bans.yml");
        load();
    }

    /** SMP-Modus: hardcore-bann=false schaltet Bann + Todes-Sperre komplett ab. */
    private boolean hardcoreAktiv() {
        return plugin.getConfig().getBoolean("hardcore-bann", true);
    }

    public void ban(UUID player, double bounty, UUID killer) {
        if (!hardcoreAktiv()) return;
        double aufschlag = plugin.getConfig().getDouble("freikauf-aufschlag", 0.05);
        Ban b = new Ban();
        b.cost = bounty * (1.0 + aufschlag);
        b.killer = killer;
        b.time = System.currentTimeMillis();
        banned.put(player, b);
    }

    public boolean isBanned(UUID player) { return banned.containsKey(player); }
    public Ban get(UUID player) { return banned.get(player); }

    public double unbanCost(UUID player) {
        Ban b = banned.get(player);
        return b == null ? 0 : b.cost;
    }

    public void unban(UUID player) { banned.remove(player); }

    public void markDead(UUID player) {
        if (!hardcoreAktiv()) return;
        dead.add(player);
    }
    public boolean isDead(UUID player) { return dead.contains(player); }
    public void revive(UUID player) { dead.remove(player); }

    public boolean hasJoker(UUID player) { return !jokerUsed.contains(player); }
    public void useJoker(UUID player) { jokerUsed.add(player); }
    public Map<UUID, Ban> all() { return banned; }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String s : yaml.getStringList("_dead")) {
            try { dead.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        for (String s : yaml.getStringList("_joker_used")) {
            try { jokerUsed.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
        }
        for (String key : yaml.getKeys(false)) {
            if (key.startsWith("_")) continue;
            try {
                UUID uuid = UUID.fromString(key);
                Ban b = new Ban();
                if (yaml.isConfigurationSection(key)) {
                    ConfigurationSection sec = yaml.getConfigurationSection(key);
                    b.cost = sec.getDouble("cost");
                    b.time = sec.getLong("time", System.currentTimeMillis());
                    b.courtUsed = sec.getBoolean("courtUsed", false);
                    String k = sec.getString("killer");
                    if (k != null && !k.isEmpty()) b.killer = UUID.fromString(k);
                } else {
                    // Altes Format: nur der Betrag
                    b.cost = yaml.getDouble(key);
                    b.time = System.currentTimeMillis();
                }
                banned.put(uuid, b);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("_dead", dead.stream().map(UUID::toString).toList());
        yaml.set("_joker_used", jokerUsed.stream().map(UUID::toString).toList());
        banned.forEach((uuid, b) -> {
            String key = uuid.toString();
            yaml.set(key + ".cost", b.cost);
            yaml.set(key + ".time", b.time);
            yaml.set(key + ".courtUsed", b.courtUsed);
            if (b.killer != null) yaml.set(key + ".killer", b.killer.toString());
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte bans.yml nicht speichern: " + e.getMessage());
        }
    }
}
