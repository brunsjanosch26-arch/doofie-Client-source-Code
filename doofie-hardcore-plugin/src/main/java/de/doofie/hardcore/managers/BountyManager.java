package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kopfgelder: Ziel-Spieler -> (Setzer -> Betrag).
 * Mehrere Spieler koennen auf dasselbe Ziel setzen; die Summe zaehlt.
 */
public class BountyManager {

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, Map<UUID, Double>> bounties = new HashMap<>();

    public BountyManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
        load();
    }

    public void add(UUID target, UUID setter, double amount) {
        bounties.computeIfAbsent(target, t -> new HashMap<>())
                .merge(setter, amount, Double::sum);
    }

    public double total(UUID target) {
        Map<UUID, Double> map = bounties.get(target);
        if (map == null) return 0;
        return map.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public boolean hasBounty(UUID target) {
        return total(target) > 0;
    }

    public Map<UUID, Map<UUID, Double>> all() {
        return bounties;
    }

    /** Kopfgeld einloesen: entfernt alle Eintraege und gibt die Summe zurueck. */
    public double claim(UUID target) {
        double sum = total(target);
        bounties.remove(target);
        return sum;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String targetKey : yaml.getKeys(false)) {
            ConfigurationSection sec = yaml.getConfigurationSection(targetKey);
            if (sec == null) continue;
            try {
                UUID target = UUID.fromString(targetKey);
                Map<UUID, Double> map = new HashMap<>();
                for (String setterKey : sec.getKeys(false)) {
                    map.put(UUID.fromString(setterKey), sec.getDouble(setterKey));
                }
                bounties.put(target, map);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        bounties.forEach((target, map) ->
            map.forEach((setter, amount) ->
                yaml.set(target.toString() + "." + setter.toString(), amount)));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte bounties.yml nicht speichern: " + e.getMessage());
        }
    }
}
