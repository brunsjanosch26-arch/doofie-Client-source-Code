package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * /items — durchblaetterbares GUI mit ALLEN Custom-Items:
 * Doofie-Items, Rucksaecke, Stellarity- und Fabled-Roots-Items (per Loot-Table erzeugt).
 * Klick gibt das Item. Nur fuer Team-Mitglieder (doofie.items).
 */
public class ItemsCommand implements CommandExecutor, Listener {

    private static final int PAGE_SIZE = 45;

    private class ItemsGuiHolder implements InventoryHolder {
        final int page;
        Inventory inventory;
        ItemsGuiHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final HardcorePlugin plugin;
    private final Random random = new Random();
    /** Anzeige-Items in fester Reihenfolge (lazy erzeugt beim ersten /items). */
    private List<ItemStack> catalog = null;
    private final List<String> lootIds = new ArrayList<>();

    public ItemsCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                plugin.getResource("customitems.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) lootIds.add(line);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("customitems.txt nicht lesbar: " + e.getMessage());
        }
    }

    private List<ItemStack> catalog(Player p) {
        if (catalog != null) return catalog;
        List<ItemStack> list = new ArrayList<>();
        // 1) Doofie-Items
        list.add(plugin.customItems().doener());
        list.add(plugin.customItems().goetterspeer());
        list.add(plugin.customItems().godMace());
        // 2) Rucksaecke (Klick loest Give-Befehl aus, Marker im Namen)
        for (int tier = 1; tier <= 4; tier++) {
            ItemStack bp = new ItemStack(Material.BROWN_DYE);
            ItemMeta meta = bp.getItemMeta();
            meta.displayName(Component.text("Rucksack Stufe " + tier, NamedTextColor.GOLD));
            meta.lore(List.of(Component.text("Klick: per BackpackPlus erhalten", NamedTextColor.GRAY)));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "give_backpack"),
                    org.bukkit.persistence.PersistentDataType.INTEGER, tier);
            bp.setItemMeta(meta);
            list.add(bp);
        }
        // 3) Loot-Table-Items (Stellarity + Fabled Roots)
        LootContext ctx = new LootContext.Builder(p.getLocation()).build();
        int failed = 0;
        for (String id : lootIds) {
            try {
                LootTable table = Bukkit.getLootTable(NamespacedKey.fromString(id));
                if (table == null) { failed++; continue; }
                Collection<ItemStack> loot = table.populateLoot(random, ctx);
                for (ItemStack item : loot) {
                    if (item != null && !item.getType().isAir()) {
                        list.add(item.clone());
                        break; // ein Anzeige-Item pro Tabelle
                    }
                }
            } catch (Exception ignored) {
                failed++;
            }
        }
        plugin.getLogger().info("/items-Katalog: " + list.size() + " Items (" + failed + " Tabellen uebersprungen)");
        catalog = list;
        return catalog;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        if (!p.hasPermission("doofie.items")) {
            p.sendMessage(Component.text("Keine Berechtigung.", NamedTextColor.RED));
            return true;
        }
        open(p, 0);
        return true;
    }

    private void open(Player p, int page) {
        List<ItemStack> items = catalog(p);
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, pages - 1));

        ItemsGuiHolder holder = new ItemsGuiHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54,
                Component.text("Custom-Items " + (page + 1) + "/" + pages, NamedTextColor.DARK_AQUA));
        holder.inventory = inv;

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            inv.setItem(i, items.get(start + i));
        }
        if (page > 0) inv.setItem(45, navItem(Material.ARROW, "« Vorherige Seite"));
        if (page < pages - 1) inv.setItem(53, navItem(Material.ARROW, "Naechste Seite »"));
        inv.setItem(49, navItem(Material.BOOK, "Seite " + (page + 1) + " von " + pages
                + " — " + items.size() + " Items"));
        p.openInventory(inv);
    }

    private ItemStack navItem(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW));
        it.setItemMeta(meta);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ItemsGuiHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (event.getClickedInventory() != event.getInventory()) return;

        int slot = event.getSlot();
        if (slot == 45) { open(p, holder.page - 1); return; }
        if (slot == 53) { open(p, holder.page + 1); return; }
        if (slot >= PAGE_SIZE) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;

        // Rucksack-Marker? Dann per BackpackPlus-Befehl geben (echte DB-Registrierung)
        if (clicked.hasItemMeta()) {
            Integer tier = clicked.getItemMeta().getPersistentDataContainer()
                    .get(new NamespacedKey(plugin, "give_backpack"),
                         org.bukkit.persistence.PersistentDataType.INTEGER);
            if (tier != null) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                        "backpackplus give " + p.getName() + " " + tier);
                p.sendMessage(Component.text("Rucksack Stufe " + tier + " erhalten!", NamedTextColor.GREEN));
                return;
            }
        }
        ItemStack give = clicked.clone();
        p.getInventory().addItem(give).values()
                .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        p.sendMessage(Component.text("Item erhalten!", NamedTextColor.GREEN));
    }
}
