package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.AuctionManager.Auction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Auktionshaus-GUI mit Kategorien:
 * — Hauptmenue: Starter, PVP, Bauen, Essen, Redstone, Werkzeug, Legendaer,
 *   Spieler-Basar.
 * — Server-Kategorien: unbegrenzter Vorrat, Preis = Verkaufspreis + Aufschlag.
 *   Materiallisten kommen aus der Config (ah-kategorien.<id>), sonst greifen
 *   eingebaute Standardlisten. 'Legendaer' verkauft die Custom-Items
 *   (Doener, Goetterspeer) zu Config-Preisen (ah-legendaer.<id>).
 * — Spieler-Basar: alle /ah-sell-Auktionen auf eigenen Seiten, automatisch
 *   nach Kategorie einsortiert (auch Custom-Items sind verkaufbar).
 */
public class AuctionGuiListener implements Listener {

    private static final int PAGE_SIZE = 45;

    public record Category(String id, String title, Material icon, String beschreibung) {}

    private static final List<Category> KATEGORIEN = List.of(
        new Category("starter", "Starter", Material.CHEST, "Alles fuer den Anfang: Werkzeug, Bett, Fackeln."),
        new Category("pvp", "PVP", Material.DIAMOND_SWORD, "Waffen, Ruestung, Gapples, Pearls."),
        new Category("bauen", "Bauen", Material.BRICKS, "Bausteine, Glas, Beton, Licht."),
        new Category("essen", "Essen", Material.COOKED_BEEF, "Satt werden — vom Brot bis zum Kuchen."),
        new Category("redstone", "Redstone", Material.REDSTONE, "Technik: Pistons, Hopper, Schienen."),
        new Category("werkzeug", "Werkzeug", Material.IRON_PICKAXE, "Werkzeuge, Eimer, Amboss & Co."),
        new Category("legendaer", "Legendaer", Material.NETHER_STAR, "Die Custom-Items des Servers."),
        new Category("basar", "Spieler-Basar", Material.PLAYER_HEAD, "Von Spielern verkaufte Items."));

    /** Eingebaute Standard-Sortimente, falls die Config nichts definiert. */
    private static final Map<String, List<String>> STANDARD_SORTIMENT = Map.of(
        "starter", List.of("BREAD", "TORCH", "OAK_LOG", "OAK_PLANKS", "LADDER", "ARROW", "BONE",
            "STRING", "CRAFTING_TABLE", "FURNACE", "WHITE_BED", "STONE_SWORD", "STONE_PICKAXE",
            "STONE_AXE", "STONE_SHOVEL", "LEATHER_HELMET", "LEATHER_CHESTPLATE",
            "LEATHER_LEGGINGS", "LEATHER_BOOTS"),
        "pvp", List.of("DIAMOND_SWORD", "DIAMOND_AXE", "BOW", "CROSSBOW", "ARROW", "SHIELD",
            "GOLDEN_APPLE", "ENCHANTED_GOLDEN_APPLE", "ENDER_PEARL", "TOTEM_OF_UNDYING",
            "IRON_HELMET", "IRON_CHESTPLATE", "IRON_LEGGINGS", "IRON_BOOTS",
            "DIAMOND_HELMET", "DIAMOND_CHESTPLATE", "DIAMOND_LEGGINGS", "DIAMOND_BOOTS",
            "COBWEB", "LAVA_BUCKET", "FLINT_AND_STEEL", "END_CRYSTAL"),
        "bauen", List.of("STONE", "STONE_BRICKS", "DEEPSLATE_BRICKS", "BRICKS", "OAK_PLANKS",
            "SPRUCE_PLANKS", "BIRCH_PLANKS", "GLASS", "GLASS_PANE", "WHITE_CONCRETE",
            "GRAY_CONCRETE", "BLACK_CONCRETE", "SANDSTONE", "QUARTZ_BLOCK", "SCAFFOLDING",
            "DIRT", "COBBLESTONE", "LANTERN", "SEA_LANTERN", "GLOWSTONE"),
        "essen", List.of("BREAD", "COOKED_BEEF", "COOKED_PORKCHOP", "COOKED_CHICKEN",
            "COOKED_MUTTON", "COOKED_RABBIT", "COOKED_COD", "COOKED_SALMON", "BAKED_POTATO",
            "GOLDEN_CARROT", "PUMPKIN_PIE", "CAKE", "SWEET_BERRIES", "HONEY_BOTTLE",
            "MUSHROOM_STEW"),
        "redstone", List.of("REDSTONE", "REDSTONE_TORCH", "REDSTONE_BLOCK", "REPEATER",
            "COMPARATOR", "PISTON", "STICKY_PISTON", "OBSERVER", "HOPPER", "DROPPER",
            "DISPENSER", "LEVER", "RAIL", "POWERED_RAIL", "DETECTOR_RAIL", "MINECART",
            "SLIME_BLOCK", "TARGET", "NOTE_BLOCK", "TRIPWIRE_HOOK"),
        "werkzeug", List.of("IRON_PICKAXE", "IRON_AXE", "IRON_SHOVEL", "IRON_HOE",
            "DIAMOND_PICKAXE", "DIAMOND_AXE", "DIAMOND_SHOVEL", "DIAMOND_HOE", "SHEARS",
            "FISHING_ROD", "FLINT_AND_STEEL", "BUCKET", "WATER_BUCKET", "LAVA_BUCKET",
            "ANVIL", "ENCHANTING_TABLE", "BREWING_STAND", "EXPERIENCE_BOTTLE", "NAME_TAG",
            "SADDLE", "LEAD", "SPYGLASS"));

    /** Standard-Preise der Legendaer-Kategorie (Config ah-legendaer.<id> gewinnt). */
    private static final Map<String, Double> LEGENDAER_STANDARD = Map.of(
        "doener", 2500.0,
        "goetterspeer", 30000.0);

    /**
     * Eintrag im GUI: Server-Material, Server-Custom-Item (customId)
     * oder Spieler-Auktion (auctionId).
     */
    public record SlotEntry(Material material, String customId, double price, UUID auctionId) {}

    /** Merkt sich Kategorie + Seite des offenen GUIs. kategorie == null: Hauptmenue. */
    public static class AhHolder implements InventoryHolder {
        public final String kategorie;
        public final int page;
        public final List<SlotEntry> entries = new ArrayList<>();
        public AhHolder(String kategorie, int page) { this.kategorie = kategorie; this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }

    private final HardcorePlugin plugin;

    public AuctionGuiListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Einstieg (auch fuer /ah): oeffnet das Kategorien-Hauptmenue. */
    public static void openGui(HardcorePlugin plugin, Player player, int page) {
        openHauptmenue(plugin, player);
    }

    public static void openHauptmenue(HardcorePlugin plugin, Player player) {
        AhHolder holder = new AhHolder(null, 0);
        Inventory inv = Bukkit.createInventory(holder, 27,
            Component.text("Auktionshaus", NamedTextColor.DARK_RED));

        int slot = 10;
        for (Category kat : KATEGORIEN) {
            ItemStack icon = new ItemStack(kat.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(Component.text(kat.title(), NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text(kat.beschreibung(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Klick zum Oeffnen", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
            icon.setItemMeta(meta);
            inv.setItem(slot++, icon);
        }
        inv.setItem(22, navItem(Material.GOLD_INGOT, "Dein Guthaben: "
            + HardcorePlugin.dollar(plugin.economy().get(player.getUniqueId()))));

        player.openInventory(inv);
    }

    /** Sortiment einer Server-Kategorie: Config gewinnt, sonst Standardliste. */
    private static List<Material> sortiment(HardcorePlugin plugin, String katId) {
        List<String> namen = plugin.getConfig().getStringList("ah-kategorien." + katId);
        if (namen.isEmpty()) namen = STANDARD_SORTIMENT.getOrDefault(katId, List.of());
        List<Material> result = new ArrayList<>();
        for (String n : namen) {
            Material m = Material.matchMaterial(n);
            if (m != null && !result.contains(m)) result.add(m);
        }
        return result;
    }

    /** Legendaer-Angebot: Custom-Item-Id -> Preis (Config-Overrides moeglich). */
    private static Map<String, Double> legendaerAngebot(HardcorePlugin plugin) {
        Map<String, Double> angebot = new LinkedHashMap<>(LEGENDAER_STANDARD);
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("ah-legendaer");
        if (sec != null) {
            for (String id : sec.getKeys(false)) angebot.put(id, sec.getDouble(id));
        }
        return angebot;
    }

    /** Alle Eintraege einer Kategorie (noch ohne Seiten-Schnitt). */
    private static List<SlotEntry> kategorieEintraege(HardcorePlugin plugin, Player player, String katId) {
        List<SlotEntry> entries = new ArrayList<>();
        double aufschlag = plugin.getConfig().getDouble("ah-aufschlag", 0.05);

        switch (katId) {
            case "legendaer" -> legendaerAngebot(plugin).forEach((id, preis) -> {
                if (plugin.customItems().byId(id) != null && preis > 0)
                    entries.add(new SlotEntry(null, id, preis, null));
            });
            case "basar" -> plugin.auctions().all().stream()
                .sorted(Comparator
                    .comparing((Auction a) -> kategorieVon(plugin, a.item()))
                    .thenComparing(a -> a.item().getType().name()))
                .forEach(a -> entries.add(new SlotEntry(null, null, a.pricePerItem(), a.id())));
            default -> {
                for (Material m : sortiment(plugin, katId)) {
                    double base = plugin.priceOf(m);
                    if (base > 0) entries.add(new SlotEntry(m, null, base * (1 + aufschlag), null));
                }
            }
        }
        return entries;
    }

    /** Ordnet ein Spieler-Item einer Kategorie zu (fuer die Basar-Sortierung). */
    private static String kategorieVon(HardcorePlugin plugin, ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, "custom_item"), PersistentDataType.STRING)) {
            return "Legendaer";
        }
        Material m = item.getType();
        for (Category kat : KATEGORIEN) {
            if (kat.id().equals("legendaer") || kat.id().equals("basar")) continue;
            if (sortiment(plugin, kat.id()).contains(m)) return kat.title();
        }
        String n = m.name();
        if (m.isEdible()) return "Essen";
        if (n.contains("SWORD") || n.contains("AXE") || n.contains("BOW") || n.contains("HELMET")
            || n.contains("CHESTPLATE") || n.contains("LEGGINGS") || n.contains("BOOTS")
            || n.contains("TRIDENT") || n.contains("SPEAR") || n.contains("MACE")) return "PVP";
        if (n.contains("REDSTONE") || n.contains("PISTON") || n.contains("RAIL")
            || n.contains("HOPPER")) return "Redstone";
        if (n.contains("PICKAXE") || n.contains("SHOVEL") || n.contains("HOE")
            || n.contains("BUCKET")) return "Werkzeug";
        if (m.isBlock()) return "Bauen";
        return "Sonstiges";
    }

    public static void openKategorie(HardcorePlugin plugin, Player player, String katId, int page) {
        Category kat = KATEGORIEN.stream().filter(k -> k.id().equals(katId)).findFirst().orElse(null);
        if (kat == null) { openHauptmenue(plugin, player); return; }

        List<SlotEntry> alle = kategorieEintraege(plugin, player, katId);
        int maxPage = Math.max(0, (alle.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));

        AhHolder holder = new AhHolder(katId, page);
        Inventory inv = Bukkit.createInventory(holder, 54,
            Component.text("AH — " + kat.title() + " (Seite " + (page + 1) + ")", NamedTextColor.DARK_RED));

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < alle.size(); i++) {
            SlotEntry entry = alle.get(start + i);
            ItemStack display;
            List<Component> lore = new ArrayList<>();

            if (entry.material() != null) {
                // Server-Angebot (unbegrenzt)
                display = new ItemStack(entry.material());
                lore.add(Component.text("Verkauft von: Server", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Preis: " + HardcorePlugin.dollar(entry.price()) + " pro Stueck", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Vorrat: unbegrenzt", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Klick: 1 kaufen | Shift+Linksklick: ganzer Stack", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else if (entry.customId() != null) {
                // Server-Custom-Item (Legendaer)
                display = plugin.customItems().byId(entry.customId());
                if (display == null) continue;
                ItemMeta existing = display.getItemMeta();
                if (existing.hasLore() && existing.lore() != null) lore.addAll(existing.lore());
                lore.add(Component.text("Verkauft von: Server", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Preis: " + HardcorePlugin.dollar(entry.price()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Vorrat: unbegrenzt", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Klick: 1 kaufen", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                Auction a = plugin.auctions().byId(entry.auctionId());
                if (a == null) continue;
                display = a.item().clone();
                ItemMeta existing = display.getItemMeta();
                if (existing.hasLore() && existing.lore() != null) lore.addAll(existing.lore());
                lore.add(Component.text("Kategorie: " + kategorieVon(plugin, a.item()), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Verkauft von: " + a.sellerName(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Preis: " + HardcorePlugin.dollar(a.pricePerItem()) + " pro Stueck", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                if (a.amount() > 1) {
                    lore.add(Component.text("Alle " + a.amount() + ": " + HardcorePlugin.dollar(a.pricePerItem() * a.amount()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                }
                if (!a.system() && a.seller().equals(player.getUniqueId())) {
                    lore.add(Component.text("Klick: Zurueckziehen", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text("Klick: 1 kaufen | Shift+Linksklick: alle kaufen", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
                }
            }

            ItemMeta meta = display.getItemMeta();
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(i, display);
            holder.entries.add(entry);
        }

        // Navigationsleiste
        if (page > 0) inv.setItem(45, navItem(Material.ARROW, "Vorherige Seite"));
        inv.setItem(46, navItem(Material.OAK_DOOR, "Zurueck zur Uebersicht"));
        inv.setItem(49, navItem(Material.GOLD_INGOT, "Dein Guthaben: "
            + HardcorePlugin.dollar(plugin.economy().get(player.getUniqueId()))));
        if (page < maxPage) inv.setItem(53, navItem(Material.ARROW, "Naechste Seite"));

        player.openInventory(inv);
    }

    private static ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AhHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        // ── Hauptmenue: Kategorie oeffnen ──
        if (holder.kategorie == null) {
            int index = slot - 10;
            if (index >= 0 && index < KATEGORIEN.size()) {
                openKategorie(plugin, player, KATEGORIEN.get(index).id(), 0);
            }
            return;
        }

        if (slot == 45 && holder.page > 0) { openKategorie(plugin, player, holder.kategorie, holder.page - 1); return; }
        if (slot == 46) { openHauptmenue(plugin, player); return; }
        if (slot == 53) { openKategorie(plugin, player, holder.kategorie, holder.page + 1); return; }
        if (slot < 0 || slot >= holder.entries.size()) return;

        SlotEntry entry = holder.entries.get(slot);

        // ── Server-Angebot: unbegrenzter Vorrat ──
        if (entry.material() != null) {
            int buyAmount = event.isShiftClick() && event.isLeftClick() ? entry.material().getMaxStackSize() : 1;
            double cost = entry.price() * buyAmount;
            if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
                player.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(cost) + ")", NamedTextColor.RED));
                return;
            }
            giveOrDrop(player, new ItemStack(entry.material(), buyAmount));
            player.sendMessage(Component.text()
                .append(Component.text("Gekauft: ", NamedTextColor.GREEN))
                .append(Component.text(buyAmount + "x fuer ", NamedTextColor.WHITE))
                .append(Component.text(HardcorePlugin.dollar(cost), NamedTextColor.GOLD))
                .build());
            openKategorie(plugin, player, holder.kategorie, holder.page);
            return;
        }

        // ── Server-Custom-Item (Legendaer) ──
        if (entry.customId() != null) {
            if (!plugin.economy().withdraw(player.getUniqueId(), entry.price())) {
                player.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(entry.price()) + ")", NamedTextColor.RED));
                return;
            }
            giveOrDrop(player, plugin.customItems().byId(entry.customId()));
            player.sendMessage(Component.text()
                .append(Component.text("Gekauft: ", NamedTextColor.GREEN))
                .append(Component.text("1x Legendaer-Item fuer ", NamedTextColor.WHITE))
                .append(Component.text(HardcorePlugin.dollar(entry.price()), NamedTextColor.GOLD))
                .build());
            openKategorie(plugin, player, holder.kategorie, holder.page);
            return;
        }

        Auction auction = plugin.auctions().byId(entry.auctionId());
        if (auction == null) {
            player.sendMessage(Component.text("Diese Auktion gibt es nicht mehr.", NamedTextColor.RED));
            openKategorie(plugin, player, holder.kategorie, holder.page);
            return;
        }

        if (!auction.system() && auction.seller().equals(player.getUniqueId())) {
            // Eigene /ah-sell-Auktion zurueckziehen
            plugin.auctions().remove(auction.id());
            giveOrDrop(player, auction.item());
            player.sendMessage(Component.text("Auktion zurueckgezogen.", NamedTextColor.YELLOW));
            openKategorie(plugin, player, holder.kategorie, holder.page);
            return;
        }

        // Klick = 1 Stueck, Shift+Linksklick = kompletter Bestand
        int buyAmount = event.isShiftClick() && event.isLeftClick() ? auction.amount() : 1;
        double cost = auction.pricePerItem() * buyAmount;

        if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(cost) + ")", NamedTextColor.RED));
            return;
        }

        ItemStack bought = auction.item().clone();
        bought.setAmount(buyAmount);
        boolean empty = auction.reduce(buyAmount);
        if (empty) plugin.auctions().remove(auction.id());

        // Bei Spieler-Auktionen bekommt der Verkaeufer das Geld;
        // bei /sell-Items (System) wurde er schon bezahlt — Geld verschwindet.
        if (!auction.system()) {
            plugin.economy().deposit(auction.seller(), cost);
            Player seller = Bukkit.getPlayer(auction.seller());
            if (seller != null) {
                seller.sendMessage(Component.text(
                    player.getName() + " hat " + buyAmount + "x dein Item im /ah fuer " + HardcorePlugin.dollar(cost) + " gekauft!",
                    NamedTextColor.GREEN));
            }
        }

        giveOrDrop(player, bought);
        player.sendMessage(Component.text()
            .append(Component.text("Gekauft: ", NamedTextColor.GREEN))
            .append(Component.text(buyAmount + "x fuer ", NamedTextColor.WHITE))
            .append(Component.text(HardcorePlugin.dollar(cost), NamedTextColor.GOLD))
            .build());
        openKategorie(plugin, player, holder.kategorie, holder.page);
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
