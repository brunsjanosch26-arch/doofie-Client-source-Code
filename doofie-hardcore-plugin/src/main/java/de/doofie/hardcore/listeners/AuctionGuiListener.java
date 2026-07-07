package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.AuctionManager.Auction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Auktionshaus-GUI: 45 Item-Slots + Navigationsleiste unten.
 * Zuerst die dauerhaften Server-Startangebote (+5% auf den Verkaufspreis,
 * unbegrenzter Vorrat), danach die Spieler-Auktionen.
 */
public class AuctionGuiListener implements Listener {

    private static final int PAGE_SIZE = 45;

    /** Eintrag im GUI: entweder Server-Angebot (material != null) oder Spieler-Auktion. */
    public record SlotEntry(Material material, double price, UUID auctionId) {}

    /** Merkt sich, welches GUI-Inventar zu welcher Seite gehoert. */
    public static class AhHolder implements InventoryHolder {
        public final int page;
        public final List<SlotEntry> entries = new ArrayList<>();
        public AhHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }

    private final HardcorePlugin plugin;

    public AuctionGuiListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    /** Dauerhafte Server-Angebote aus der Config: Verkaufspreis +5%. */
    private static List<SlotEntry> serverOffers(HardcorePlugin plugin) {
        double aufschlag = plugin.getConfig().getDouble("ah-aufschlag", 0.05);
        List<SlotEntry> offers = new ArrayList<>();
        for (String name : plugin.getConfig().getStringList("ah-startangebot")) {
            Material material = Material.matchMaterial(name);
            if (material == null) continue;
            double base = plugin.priceOf(material);
            if (base <= 0) continue;
            offers.add(new SlotEntry(material, base * (1 + aufschlag), null));
        }
        return offers;
    }

    public static void openGui(HardcorePlugin plugin, Player player, int page) {
        // Kombinierte Liste: Server-Angebote zuerst, dann Spieler-Auktionen
        List<SlotEntry> combined = new ArrayList<>(serverOffers(plugin));
        for (Auction a : plugin.auctions().all()) {
            combined.add(new SlotEntry(null, a.pricePerItem(), a.id()));
        }

        int maxPage = Math.max(0, (combined.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));

        AhHolder holder = new AhHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54,
            Component.text("Auktionshaus — Seite " + (page + 1), NamedTextColor.DARK_RED));

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < combined.size(); i++) {
            SlotEntry entry = combined.get(start + i);
            ItemStack display;
            List<Component> lore = new ArrayList<>();

            if (entry.material() != null) {
                // Server-Startangebot (unbegrenzt)
                display = new ItemStack(entry.material());
                lore.add(Component.text("Verkauft von: Server", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Preis: " + HardcorePlugin.dollar(entry.price()) + " pro Stueck", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Vorrat: unbegrenzt", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Klick: 1 kaufen | Shift+Linksklick: ganzer Stack", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            } else {
                Auction a = plugin.auctions().byId(entry.auctionId());
                if (a == null) continue;
                display = a.item().clone();
                ItemMeta existing = display.getItemMeta();
                if (existing.hasLore() && existing.lore() != null) lore.addAll(existing.lore());
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
        if (page < maxPage) inv.setItem(53, navItem(Material.ARROW, "Naechste Seite"));
        inv.setItem(49, navItem(Material.GOLD_INGOT, "Dein Guthaben: "
            + HardcorePlugin.dollar(plugin.economy().get(player.getUniqueId()))));

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
        if (slot == 45) { openGui(plugin, player, holder.page - 1); return; }
        if (slot == 53) { openGui(plugin, player, holder.page + 1); return; }
        if (slot < 0 || slot >= holder.entries.size()) return;

        SlotEntry entry = holder.entries.get(slot);

        // ── Server-Startangebot: unbegrenzter Vorrat ──
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
            openGui(plugin, player, holder.page);
            return;
        }

        Auction auction = plugin.auctions().byId(entry.auctionId());
        if (auction == null) {
            player.sendMessage(Component.text("Diese Auktion gibt es nicht mehr.", NamedTextColor.RED));
            openGui(plugin, player, holder.page);
            return;
        }

        if (!auction.system() && auction.seller().equals(player.getUniqueId())) {
            // Eigene /ah-sell-Auktion zurueckziehen
            plugin.auctions().remove(auction.id());
            giveOrDrop(player, auction.item());
            player.sendMessage(Component.text("Auktion zurueckgezogen.", NamedTextColor.YELLOW));
            openGui(plugin, player, holder.page);
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
        openGui(plugin, player, holder.page);
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
