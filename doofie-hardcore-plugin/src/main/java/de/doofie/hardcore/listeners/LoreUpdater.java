package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Verkaufswert-Lore wurde entfernt, weil sie das Stacken kaputt gemacht hat.
 * Diese Klasse raeumt jetzt nur noch ALTE Beschriftungen von Items weg
 * (Inventare, Aufsammeln, Kisten), damit alles wieder sauber stackt.
 * Preise sieht man im /sell-Menue und im /ah.
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
        for (ItemStack item : player.getInventory().getContents()) {
            stripLore(item);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        stripLore(event.getItem().getItemStack());
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            stripLore(item);
        }
    }

    /** Entfernt die alte Verkaufswert-Zeile von einem Item. */
    public void stripLore(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith(PREFIX))) {
            meta.lore(lore.isEmpty() ? null : lore);
            item.setItemMeta(meta);
        }
    }
}
