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

/**
 * Auktionshaus. Preis gilt IMMER pro Stueck.
 * - Spieler-Auktionen (/ah sell): Erloes geht an den Verkaeufer.
 * - System-Auktionen (/sell): Spieler wurde schon bezahlt, Items werden
 *   mit +5% Aufschlag angeboten, der Erloes verschwindet (Geld-Senke).
 */
public class AuctionManager {

    public static class Auction {
        private final UUID id;
        private final UUID seller;
        private final String sellerName;
        private final double pricePerItem;
        private final ItemStack item;
        private final boolean system;

        public Auction(UUID id, UUID seller, String sellerName, double pricePerItem, ItemStack item, boolean system) {
            this.id = id;
            this.seller = seller;
            this.sellerName = sellerName;
            this.pricePerItem = pricePerItem;
            this.item = item;
            this.system = system;
        }

        public UUID id() { return id; }
        public UUID seller() { return seller; }
        public String sellerName() { return sellerName; }
        public double pricePerItem() { return pricePerItem; }
        public ItemStack item() { return item; }
        public boolean system() { return system; }
        public int amount() { return item.getAmount(); }

        /** Bestand reduzieren; @return true wenn die Auktion leer ist. */
        public boolean reduce(int by) {
            int rest = item.getAmount() - by;
            item.setAmount(Math.max(rest, 0));
            return rest <= 0;
        }
    }

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
        return auctions.stream().filter(a -> !a.system() && a.seller().equals(seller)).count();
    }

    public void add(UUID seller, String sellerName, double pricePerItem, ItemStack item, boolean system) {
        ItemStack stack = item.clone();

        if (system) {
            // Gleiche System-Angebote desselben Verkaeufers zusammenlegen
            for (Auction a : auctions) {
                if (a.system()
                        && a.seller().equals(seller)
                        && Math.abs(a.pricePerItem() - pricePerItem) < 0.001
                        && a.item().isSimilar(stack)
                        && a.amount() + stack.getAmount() <= stack.getMaxStackSize()) {
                    a.item().setAmount(a.amount() + stack.getAmount());
                    return;
                }
            }
        }
        auctions.add(new Auction(UUID.randomUUID(), seller, sellerName, pricePerItem, stack, system));
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
                    item,
                    sec.getBoolean("system", false)
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
            yaml.set(key + ".price", a.pricePerItem());
            yaml.set(key + ".item", a.item());
            yaml.set(key + ".system", a.system());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte auctions.yml nicht speichern: " + e.getMessage());
        }
    }
}
