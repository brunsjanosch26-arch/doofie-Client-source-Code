package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kopfgeld-Bann: Spieler -> Freikauf-Betrag (Kopfgeld + Aufschlag).
 * Gebannte duerfen joinen, sind aber Spectator bis sie freigekauft werden.
 */
public class BanManager {

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, Double> banned = new HashMap<>();

    public BanManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bans.yml");
        load();
    }

    public void ban(UUID player, double bounty) {
        double aufschlag = plugin.getConfig().getDouble("freikauf-aufschlag", 0.05);
        banned.put(player, bounty * (1.0 + aufschlag));
    }

    public boolean isBanned(UUID player) {
        return banned.containsKey(player);
    }

    /** Freikauf-Kosten (Kopfgeld + 5%). */
    public double unbanCost(UUID player) {
        return banned.getOrDefault(player, 0.0);
    }

    public void unban(UUID player) {
        banned.remove(player);
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                banned.put(UUID.fromString(key), yaml.getDouble(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        banned.forEach((uuid, cost) -> yaml.set(uuid.toString(), cost));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte bans.yml nicht speichern: " + e.getMessage());
        }
    }
}
