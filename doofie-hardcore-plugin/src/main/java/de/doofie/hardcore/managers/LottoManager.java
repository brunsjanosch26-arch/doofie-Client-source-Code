package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Stuendliche Lotterie: Tickets kaufen, Pot gewinnen. */
public class LottoManager {

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, Integer> tickets = new HashMap<>();
    private double pot = 0;
    private final Random random = new Random();

    public LottoManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "lotto.yml");
        load();
        // Ziehung jede Stunde
        Bukkit.getScheduler().runTaskTimer(plugin, this::draw, 20L * 3600, 20L * 3600);
    }

    public double ticketPrice() {
        return plugin.getConfig().getDouble("lotto-ticket-preis", 50.0);
    }

    public void buy(UUID player, int count) {
        tickets.merge(player, count, Integer::sum);
        pot += ticketPrice() * count;
    }

    public int ticketsOf(UUID player) {
        return tickets.getOrDefault(player, 0);
    }

    public double pot() {
        return pot;
    }

    public int totalTickets() {
        return tickets.values().stream().mapToInt(Integer::intValue).sum();
    }

    public void draw() {
        int total = totalTickets();
        if (total == 0 || pot <= 0) return;

        List<UUID> pool = new ArrayList<>();
        tickets.forEach((uuid, count) -> {
            for (int i = 0; i < count; i++) pool.add(uuid);
        });
        UUID winner = pool.get(random.nextInt(pool.size()));
        double prize = pot;

        plugin.economy().deposit(winner, prize);
        tickets.clear();
        pot = 0;

        OfflinePlayer p = Bukkit.getOfflinePlayer(winner);
        Bukkit.broadcast(Component.text()
            .append(Component.text("LOTTO! ", NamedTextColor.LIGHT_PURPLE))
            .append(Component.text(String.valueOf(p.getName()), NamedTextColor.GOLD))
            .append(Component.text(" gewinnt den Pot von ", NamedTextColor.GRAY))
            .append(Component.text(HardcorePlugin.dollar(prize), NamedTextColor.GOLD))
            .append(Component.text("! (" + total + " Tickets)", NamedTextColor.GRAY))
            .build());
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        pot = yaml.getDouble("pot", 0);
        var sec = yaml.getConfigurationSection("tickets");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    tickets.put(UUID.fromString(key), sec.getInt(key));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("pot", pot);
        tickets.forEach((uuid, count) -> yaml.set("tickets." + uuid, count));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte lotto.yml nicht speichern: " + e.getMessage());
        }
    }
}
