package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Taegliche Belohnung mit Streak-Bonus. */
public class DailyManager {

    public record State(String lastDate, int streak) {}

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, State> states = new HashMap<>();

    public DailyManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily.yml");
        load();
    }

    /** @return Belohnung in Dollar, oder -1 wenn heute schon abgeholt */
    public double claim(UUID player) {
        String today = LocalDate.now().toString();
        String yesterday = LocalDate.now().minusDays(1).toString();
        State s = states.get(player);

        if (s != null && today.equals(s.lastDate())) return -1;

        int streak = (s != null && yesterday.equals(s.lastDate())) ? s.streak() + 1 : 1;
        states.put(player, new State(today, streak));

        double base = plugin.getConfig().getDouble("daily-belohnung", 100.0);
        double streakBonus = plugin.getConfig().getDouble("daily-streak-belohnung", 500.0);
        return streak >= 7 ? streakBonus : base;
    }

    public int streak(UUID player) {
        State s = states.get(player);
        return s == null ? 0 : s.streak();
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                states.put(UUID.fromString(key),
                    new State(yaml.getString(key + ".date", ""), yaml.getInt(key + ".streak", 0)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        states.forEach((uuid, s) -> {
            yaml.set(uuid.toString() + ".date", s.lastDate());
            yaml.set(uuid.toString() + ".streak", s.streak());
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte daily.yml nicht speichern: " + e.getMessage());
        }
    }
}
