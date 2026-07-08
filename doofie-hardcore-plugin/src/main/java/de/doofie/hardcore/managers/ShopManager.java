package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Kistenshops: Schild an Kiste, andere kaufen direkt daraus — auch offline. */
public class ShopManager {

    public record Shop(UUID owner, String ownerName, double price, Location sign, Location chest) {}

    private final HardcorePlugin plugin;
    private final File file;
    /** Key = Schild-Location als String */
    private final Map<String, Shop> shops = new HashMap<>();

    public ShopManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "shops.yml");
        load();
    }

    public static String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public Shop bySign(Location sign) { return shops.get(key(sign)); }

    public Shop byChest(Location chest) {
        String k = key(chest);
        return shops.values().stream().filter(s -> key(s.chest()).equals(k)).findFirst().orElse(null);
    }

    public void add(Shop shop) { shops.put(key(shop.sign()), shop); }
    public void remove(Location sign) { shops.remove(key(sign)); }
    public Map<String, Shop> all() { return shops; }

    private static Location parse(String s) {
        String[] p = s.split(";");
        return new Location(Bukkit.getWorld(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String k : yaml.getKeys(false)) {
            ConfigurationSection sec = yaml.getConfigurationSection(k);
            if (sec == null) continue;
            try {
                Location sign = parse(sec.getString("sign", ""));
                Location chest = parse(sec.getString("chest", ""));
                if (sign.getWorld() == null || chest.getWorld() == null) continue;
                shops.put(k, new Shop(
                    UUID.fromString(sec.getString("owner", "")),
                    sec.getString("ownerName", "?"),
                    sec.getDouble("price"), sign, chest));
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        shops.forEach((k, s) -> {
            yaml.set(k + ".owner", s.owner().toString());
            yaml.set(k + ".ownerName", s.ownerName());
            yaml.set(k + ".price", s.price());
            yaml.set(k + ".sign", key(s.sign()));
            yaml.set(k + ".chest", key(s.chest()));
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte shops.yml nicht speichern: " + e.getMessage());
        }
    }
}
