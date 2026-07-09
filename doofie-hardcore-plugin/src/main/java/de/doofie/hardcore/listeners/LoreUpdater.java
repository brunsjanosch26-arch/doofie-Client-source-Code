package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Zeigt unter jedem Item den Verkaufswert pro Stueck an.
 * Damit Items trotzdem normal stacken, wird die Lore UEBERALL identisch
 * angewendet: beim Aufsammeln (vor dem Einsortieren), beim Oeffnen von
 * Kisten und regelmaessig im Inventar. Beim Villager-Handel wird sie
 * entfernt, damit Trades das Item akzeptieren.
 */
public class LoreUpdater implements Listener {

    private static final String PREFIX = "Verkaufswert: ";

    private final HardcorePlugin plugin;

    public LoreUpdater(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateInventory(player);
            }
        }, 60L, 60L);
    }

    public void updateInventory(Player player) {
        // Waehrend eines Villager-Handels: Lore entfernen statt anwenden,
        // sonst akzeptiert der Trade die Items nicht
        boolean trading = player.getOpenInventory().getTopInventory() instanceof MerchantInventory;
        for (ItemStack item : player.getInventory().getContents()) {
            if (trading) stripLore(item);
            else applyLore(item);
        }
    }

    /** Beim Aufsammeln sofort beschriften — dann stackt es mit Inventar-Items. */
    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        applyLore(event.getItem().getItemStack());
    }

    /** Kisten/Faesser/Shulker beim Oeffnen beschriften — dann stackt Kiste<->Inventar. */
    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        var top = event.getInventory();
        if (top instanceof MerchantInventory) {
            if (event.getPlayer() instanceof Player p) {
                for (ItemStack item : p.getInventory().getContents()) stripLore(item);
            }
            return;
        }
        if (top.getHolder() instanceof org.bukkit.inventory.InventoryHolder) {
            for (ItemStack item : top.getContents()) applyLore(item);
        }
    }

    /** Entfernt die Verkaufswert-Zeile wieder (fuer Villager-Trades). */
    public void stripLore(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith(PREFIX))) {
            meta.lore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
    }

    /** Setzt/aktualisiert die Verkaufswert-Zeile in der Lore eines Items. */
    public void applyLore(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        double price = plugin.priceOfItem(item);

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();

        boolean had = lore.removeIf(line ->
            PlainTextComponentSerializer.plainText().serialize(line).startsWith(PREFIX));

        if (price > 0) {
            lore.add(Component.text(PREFIX + HardcorePlugin.dollar(price) + " pro Stueck", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        } else if (!had) {
            return; // nichts zu tun
        }

        meta.lore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }
}
