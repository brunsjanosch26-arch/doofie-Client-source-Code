package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Auktionshaus: Items mit Sofortkauf-Preis, gespeichert in auctions.yml. */
public class AuctionManager {

    public record Auction(UUID id, UUID seller, String sellerName, double price, ItemStack item) {}

    private final HardcorePlugin plugin;
    private final File file;
    private final List<Auction> auctions = new ArrayList<>();

    public AuctionManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "auctions.yml");
        load();
    }

    public List<Auction> all() {
        return auctions;
    }

    public long countBySeller(UUID seller) {
        return auctions.stream().filter(a -> a.seller().equals(seller)).count();
    }

    public void add(UUID seller, String sellerName, double price, ItemStack item) {
        auctions.add(new Auction(UUID.randomUUID(), seller, sellerName, price, item.clone()));
    }

    public Auction byId(UUID id) {
        return auctions.stream().filter(a -> a.id().equals(id)).findFirst().orElse(null);
    }

    public void remove(UUID id) {
        auctions.removeIf(a -> a.id().equals(id));
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection sec = yaml.getConfigurationSection(key);
            if (sec == null) continue;
            try {
                ItemStack item = sec.getItemStack("item");
                if (item == null) continue;
                auctions.add(new Auction(
                    UUID.fromString(key),
                    UUID.fromString(sec.getString("seller", "")),
                    sec.getString("sellerName", "?"),
                    sec.getDouble("price"),
                    item
                ));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Auction a : auctions) {
            String key = a.id().toString();
            yaml.set(key + ".seller", a.seller().toString());
            yaml.set(key + ".sellerName", a.sellerName());
            yaml.set(key + ".price", a.price());
            yaml.set(key + ".item", a.item());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte auctions.yml nicht speichern: " + e.getMessage());
        }
    }
}
