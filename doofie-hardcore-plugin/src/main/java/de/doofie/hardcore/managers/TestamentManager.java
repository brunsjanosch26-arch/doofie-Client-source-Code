package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vermaechtnis: Bleibt ein Spieler 7 Tage gebannt, erbt sein Beguenstigter
 * die Haelfte des Vermoegens — der Rest wandert in den Lotto-Pot.
 */
public class TestamentManager {

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, UUID> heirs = new HashMap<>();

    public TestamentManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "testament.yml");
        load();
        // Stuendlich pruefen
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkInheritances, 20L * 120, 20L * 3600);
    }

    public void setHeir(UUID player, UUID heir) { heirs.put(player, heir); }
    public UUID heirOf(UUID player) { return heirs.get(player); }

    private void checkInheritances() {
        long sevenDays = 7L * 24 * 60 * 60 * 1000;
        for (var entry : Map.copyOf(plugin.bans().all()).entrySet()) {
            UUID banned = entry.getKey();
            if (System.currentTimeMillis() - entry.getValue().time < sevenDays) continue;
            UUID heir = heirs.remove(banned);
            if (heir == null) continue;

            double wealth = plugin.economy().get(banned);
            if (wealth <= 0) continue;
            double half = wealth / 2;
            plugin.economy().withdraw(banned, wealth);
            plugin.economy().deposit(heir, half);
            plugin.lotto().addToPot(half);

            String bannedName = Bukkit.getOfflinePlayer(banned).getName();
            String heirName = Bukkit.getOfflinePlayer(heir).getName();
            Bukkit.broadcast(Component.text()
                .append(Component.text("VERMAECHTNIS! ", NamedTextColor.DARK_PURPLE))
                .append(Component.text(bannedName + " ist seit 7 Tagen gebannt — " + heirName
                    + " erbt " + HardcorePlugin.dollar(half) + ", die andere Haelfte fliesst in den Lotto-Pot!", NamedTextColor.GRAY))
                .build());
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                heirs.put(UUID.fromString(key), UUID.fromString(yaml.getString(key, "")));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        heirs.forEach((p, h) -> yaml.set(p.toString(), h.toString()));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte testament.yml nicht speichern: " + e.getMessage());
        }
    }
}
