package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * /sell-Menue: leeres Fenster, Items beliebig reinlegen,
 * gruener Knopf = verkaufen, roter Knopf = abbrechen.
 * Nicht verkaufbare Items kommen zurueck ins Inventar.
 */
public class SellMenuListener implements Listener {

    private static final int ITEM_SLOTS = 45;      // 0..44 frei fuer Items
    private static final int SLOT_CANCEL = 45;
    private static final int SLOT_CONFIRM = 49;

    public static class SellHolder implements InventoryHolder {
        public boolean confirmed = false;
        @Override public Inventory getInventory() { return null; }
    }

    private final HardcorePlugin plugin;

    public SellMenuListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    public static void openMenu(HardcorePlugin plugin, Player player) {
        Inventory inv = Bukkit.createInventory(new SellHolder(), 54,
            Component.text("Verkaufen — Items reinlegen", NamedTextColor.DARK_GREEN));

        ItemStack filler = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.GRAY);
        for (int i = ITEM_SLOTS; i < 54; i++) inv.setItem(i, filler);
        inv.setItem(SLOT_CANCEL, button(Material.BARRIER, "Abbrechen", NamedTextColor.RED));
        inv.setItem(SLOT_CONFIRM, button(Material.EMERALD_BLOCK, "VERKAUFEN — bestaetigen", NamedTextColor.GREEN));

        player.openInventory(inv);
    }

    private static ItemStack button(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false).decorate(TextDecoration.BOLD));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        // Klicks im eigenen Inventar (inkl. Shift-Verschieben) sind erlaubt —
        // die Button-Reihe ist mit Panes gefuellt, Shift landet nur in 0..44.
        if (slot >= ITEM_SLOTS && slot < 54) {
            event.setCancelled(true);

            if (slot == SLOT_CANCEL) {
                player.closeInventory(); // onClose gibt Items zurueck
            } else if (slot == SLOT_CONFIRM) {
                holder.confirmed = true;
                sellContents(player, event.getInventory());
                player.closeInventory();
            }
        }
    }

    private void sellContents(Player player, Inventory inv) {
        double earned = 0;
        int soldItems = 0;
        int returned = 0;
        double aufschlag = plugin.getConfig().getDouble("ah-aufschlag", 0.05);

        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType().isAir()) continue;

            double price = plugin.priceOfItem(item);
            if (price > 0) {
                earned += price * item.getAmount();
                soldItems += item.getAmount();
                // Verkaufte Items landen mit +5% als Angebot im /ah
                plugin.auctions().add(player.getUniqueId(), player.getName(),
                    price * (1 + aufschlag), item, true);
            } else {
                giveOrDrop(player, item);
                returned++;
            }
            inv.setItem(i, null);
        }

        if (soldItems > 0) {
            double mult = plugin.events().sellMultiplier();
            if (mult > 1) {
                earned *= mult;
                player.sendMessage(Component.text("GOLDRAUSCH: Doppelter Erloes!", NamedTextColor.GOLD));
            }
            plugin.economy().deposit(player.getUniqueId(), earned);
            plugin.quests().progress(player, QuestManager.Type.SELL, soldItems);
            player.sendMessage(Component.text()
                .append(Component.text("Verkauft: ", NamedTextColor.GREEN))
                .append(Component.text(soldItems + " Items", NamedTextColor.WHITE))
                .append(Component.text(" fuer ", NamedTextColor.GREEN))
                .append(Component.text(HardcorePlugin.dollar(earned), NamedTextColor.GOLD))
                .append(Component.text(" — deine Items sind jetzt mit Aufschlag im /ah!", NamedTextColor.GRAY))
                .build());
        } else {
            player.sendMessage(Component.text("Nichts Verkaufbares im Menue.", NamedTextColor.RED));
        }
        if (returned > 0) {
            player.sendMessage(Component.text(returned + " Item(s) ohne Verkaufswert zurueckgegeben.", NamedTextColor.GRAY));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        if (holder.confirmed) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        // Abbruch/Schliessen: alle reingelegten Items zurueckgeben
        for (int i = 0; i < ITEM_SLOTS; i++) {
            ItemStack item = event.getInventory().getItem(i);
            if (item != null && !item.getType().isAir()) {
                giveOrDrop(player, item);
                event.getInventory().setItem(i, null);
            }
        }
    }

    private static void giveOrDrop(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
    }
}
