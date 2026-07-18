package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Monats-Saisons: Am Monatswechsel bekommen die Top 3 der Kill-Rangliste
 * eine Praemie, danach werden die Kills zurueckgesetzt.
 */
public class SeasonManager {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final HardcorePlugin plugin;
    private final File file;
    private String season;

    public SeasonManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "saison.yml");
        load();
        checkRollover();
        // Einmal pro Stunde pruefen (faengt Monatswechsel bei laufendem Server)
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkRollover, 20L * 3600, 20L * 3600);
    }

    public String current() { return season; }

    /** Tage bis zum Saisonende (Monatsende). */
    public long daysLeft() {
        LocalDate now = LocalDate.now();
        return now.lengthOfMonth() - now.getDayOfMonth() + 1;
    }

    private void checkRollover() {
        String now = LocalDate.now().format(FMT);
        if (now.equals(season)) return;
        if (season != null) payout();
        season = now;
        save();
    }

    private void payout() {
        Map<UUID, Integer> kills = plugin.stats().kills();
        List<Map.Entry<UUID, Integer>> top = kills.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(3).toList();
        double[] rewards = {
                plugin.getConfig().getDouble("saison-preis-1", 10000),
                plugin.getConfig().getDouble("saison-preis-2", 5000),
                plugin.getConfig().getDouble("saison-preis-3", 2500),
        };
        StringBuilder msg = new StringBuilder("SAISON " + season + " VORBEI!");
        for (int i = 0; i < top.size(); i++) {
            UUID id = top.get(i).getKey();
            plugin.economy().deposit(id, rewards[i]);
            String name = Bukkit.getOfflinePlayer(id).getName();
            msg.append(" #").append(i + 1).append(" ").append(name == null ? "?" : name)
               .append(" (").append(top.get(i).getValue()).append(" Kills, +")
               .append(HardcorePlugin.dollar(rewards[i])).append(")");
        }
        if (!top.isEmpty()) {
            Bukkit.broadcast(Component.text(msg.toString(), NamedTextColor.GOLD));
        }
        plugin.stats().resetKills();
        plugin.getLogger().info("Saison " + season + " abgeschlossen, Kills zurueckgesetzt.");
    }

    private void load() {
        if (!file.exists()) { season = null; return; }
        season = YamlConfiguration.loadConfiguration(file).getString("saison");
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("saison", season);
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte saison.yml nicht speichern: " + ex.getMessage());
        }
    }
}
