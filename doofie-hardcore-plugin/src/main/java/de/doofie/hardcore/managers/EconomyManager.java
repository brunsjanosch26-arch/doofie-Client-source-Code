package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Dollar-Konten aller Spieler, gespeichert in economy.yml. */
public class EconomyManager {

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, Double> balances = new HashMap<>();

    public EconomyManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "economy.yml");
        load();
    }

    public double get(UUID player) {
        return balances.computeIfAbsent(player, p -> plugin.getConfig().getDouble("start-guthaben", 500.0));
    }

    public boolean has(UUID player, double amount) {
        return get(player) >= amount;
    }

    public void deposit(UUID player, double amount) {
        balances.put(player, get(player) + amount);
    }

    /** @return false wenn nicht genug Geld vorhanden ist */
    public boolean withdraw(UUID player, double amount) {
        if (!has(player, amount)) return false;
        balances.put(player, get(player) - amount);
        return true;
    }

    /** Alle Konten (fuer /top geld). */
    public Map<UUID, Double> allBalances() {
        return balances;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                balances.put(UUID.fromString(key), yaml.getDouble(key));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        balances.forEach((uuid, bal) -> yaml.set(uuid.toString(), bal));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte economy.yml nicht speichern: " + e.getMessage());
        }
    }
}
