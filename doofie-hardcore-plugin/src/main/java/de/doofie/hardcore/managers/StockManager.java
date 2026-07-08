package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Boerse: 6 fiktive Firmen, Kurse bewegen sich alle 10 Minuten. */
public class StockManager {

    private final HardcorePlugin plugin;
    private final File file;
    private final Random random = new Random();
    /** Firma -> aktueller Kurs */
    private final Map<String, Double> prices = new LinkedHashMap<>();
    /** Firma -> Kurs vor 10 Minuten (fuer Trend-Anzeige) */
    private final Map<String, Double> lastPrices = new HashMap<>();
    /** Spieler -> (Firma -> Anzahl) */
    private final Map<UUID, Map<String, Integer>> holdings = new HashMap<>();

    private static final Map<String, Double> START = new LinkedHashMap<>() {{
        put("CreeperAG", 100.0);
        put("NetheriteCorp", 500.0);
        put("RedstoneTech", 80.0);
        put("EmeraldBank", 200.0);
        put("SlimeWorks", 40.0);
        put("EnderExpress", 150.0);
    }};

    public StockManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stocks.yml");
        START.forEach(prices::put);
        load();
        prices.forEach(lastPrices::put);
        // Kursbewegung alle 10 Minuten: -8% bis +8%
        Bukkit.getScheduler().runTaskTimer(plugin, this::moveMarket, 20L * 600, 20L * 600);
    }

    private void moveMarket() {
        prices.forEach((firm, price) -> {
            lastPrices.put(firm, price);
            double factor = 0.92 + random.nextDouble() * 0.16;
            double next = Math.max(5, price * factor);
            prices.put(firm, Math.round(next * 100) / 100.0);
        });
    }

    public Map<String, Double> prices() { return prices; }

    public double trend(String firm) {
        double last = lastPrices.getOrDefault(firm, prices.getOrDefault(firm, 0.0));
        double now = prices.getOrDefault(firm, 0.0);
        return last == 0 ? 0 : (now - last) / last * 100;
    }

    public String matchFirm(String input) {
        return prices.keySet().stream()
            .filter(f -> f.equalsIgnoreCase(input)).findFirst().orElse(null);
    }

    public int owned(UUID player, String firm) {
        return holdings.getOrDefault(player, Map.of()).getOrDefault(firm, 0);
    }

    public Map<String, Integer> portfolio(UUID player) {
        return holdings.getOrDefault(player, Map.of());
    }

    public void buy(UUID player, String firm, int count) {
        holdings.computeIfAbsent(player, p -> new HashMap<>()).merge(firm, count, Integer::sum);
    }

    public boolean sell(UUID player, String firm, int count) {
        Map<String, Integer> h = holdings.get(player);
        if (h == null || h.getOrDefault(firm, 0) < count) return false;
        h.merge(firm, -count, Integer::sum);
        return true;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection p = yaml.getConfigurationSection("prices");
        if (p != null) {
            for (String firm : p.getKeys(false)) {
                if (prices.containsKey(firm)) prices.put(firm, p.getDouble(firm));
            }
        }
        ConfigurationSection h = yaml.getConfigurationSection("holdings");
        if (h != null) {
            for (String uuid : h.getKeys(false)) {
                try {
                    Map<String, Integer> map = new HashMap<>();
                    ConfigurationSection sec = h.getConfigurationSection(uuid);
                    if (sec != null) {
                        for (String firm : sec.getKeys(false)) map.put(firm, sec.getInt(firm));
                    }
                    holdings.put(UUID.fromString(uuid), map);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        prices.forEach((firm, price) -> yaml.set("prices." + firm, price));
        holdings.forEach((uuid, map) -> map.forEach((firm, count) -> {
            if (count > 0) yaml.set("holdings." + uuid + "." + firm, count);
        }));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte stocks.yml nicht speichern: " + e.getMessage());
        }
    }
}
