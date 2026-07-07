package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Zeigt unter jedem Item im Inventar den Verkaufswert pro Stueck an.
 * Laeuft alle 3 Sekunden ueber alle Online-Spieler.
 */
public class LoreUpdater {

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
            applyLore(item);
        }
    }

    /** Setzt/aktualisiert die Verkaufswert-Zeile in der Lore eines Items. */
    public void applyLore(ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        double price = plugin.priceOf(item.getType());

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
