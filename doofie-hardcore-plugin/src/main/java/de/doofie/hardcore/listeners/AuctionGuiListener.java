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

/** Auktionshaus-GUI: 45 Item-Slots + Navigationsleiste unten. */
public class AuctionGuiListener implements Listener {

    private static final int PAGE_SIZE = 45;

    /** Merkt sich, welches GUI-Inventar zu welcher Seite gehoert. */
    public static class AhHolder implements InventoryHolder {
        public final int page;
        public final List<UUID> slotAuctionIds = new ArrayList<>();
        public AhHolder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return null; }
    }

    private final HardcorePlugin plugin;

    public AuctionGuiListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    public static void openGui(HardcorePlugin plugin, Player player, int page) {
        List<Auction> all = plugin.auctions().all();
        int maxPage = Math.max(0, (all.size() - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, maxPage));

        AhHolder holder = new AhHolder(page);
        Inventory inv = Bukkit.createInventory(holder, 54,
            Component.text("Auktionshaus — Seite " + (page + 1), NamedTextColor.DARK_RED));

        int start = page * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < all.size(); i++) {
            Auction a = all.get(start + i);
            ItemStack display = a.item().clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.text("Preis: " + HardcorePlugin.dollar(a.price()), NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Verkaeufer: " + a.sellerName(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text(a.seller().equals(player.getUniqueId())
                ? "Klicken zum Zurueckziehen" : "Klicken zum Kaufen", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            display.setItemMeta(meta);
            inv.setItem(i, display);
            holder.slotAuctionIds.add(a.id());
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
        if (slot < 0 || slot >= holder.slotAuctionIds.size()) return;

        Auction auction = plugin.auctions().byId(holder.slotAuctionIds.get(slot));
        if (auction == null) {
            player.sendMessage(Component.text("Diese Auktion gibt es nicht mehr.", NamedTextColor.RED));
            openGui(plugin, player, holder.page);
            return;
        }

        if (auction.seller().equals(player.getUniqueId())) {
            // Eigene Auktion zurueckziehen
            plugin.auctions().remove(auction.id());
            giveOrDrop(player, auction.item());
            player.sendMessage(Component.text("Auktion zurueckgezogen.", NamedTextColor.YELLOW));
            openGui(plugin, player, holder.page);
            return;
        }

        if (!plugin.economy().withdraw(player.getUniqueId(), auction.price())) {
            player.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(auction.price()) + ")", NamedTextColor.RED));
            return;
        }

        plugin.auctions().remove(auction.id());
        plugin.economy().deposit(auction.seller(), auction.price());
        giveOrDrop(player, auction.item());

        player.sendMessage(Component.text()
            .append(Component.text("Gekauft fuer ", NamedTextColor.GREEN))
            .append(Component.text(HardcorePlugin.dollar(auction.price()), NamedTextColor.GOLD))
            .build());
        Player seller = Bukkit.getPlayer(auction.seller());
        if (seller != null) {
            seller.sendMessage(Component.text(
                player.getName() + " hat dein Item im /ah fuer " + HardcorePlugin.dollar(auction.price()) + " gekauft!",
                NamedTextColor.GREEN));
        }
        openGui(plugin, player, holder.page);
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
