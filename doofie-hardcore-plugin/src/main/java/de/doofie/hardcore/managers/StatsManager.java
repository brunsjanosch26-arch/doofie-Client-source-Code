package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Spieler-Kill-Statistik fuer /top kills. */
public class StatsManager {

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, Integer> kills = new HashMap<>();

    public StatsManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public void addKill(UUID player) {
        kills.merge(player, 1, Integer::sum);
    }

    public Map<UUID, Integer> kills() {
        return kills;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                kills.put(UUID.fromString(key), yaml.getInt(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        kills.forEach((uuid, k) -> yaml.set(uuid.toString(), k));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte stats.yml nicht speichern: " + e.getMessage());
        }
    }
}
