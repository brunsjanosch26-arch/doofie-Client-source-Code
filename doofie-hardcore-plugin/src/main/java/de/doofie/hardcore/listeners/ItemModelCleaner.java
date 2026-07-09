package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Aufraeum-Listener: Der fruehere ItemModelStamper hat JEDEM Item ein
 * item_model 'doofie:<name>' verpasst. Das ist zurueckgenommen — nur die
 * Custom-Items (Hermes-Ruestung, Doener, Goetterspeer) behalten ihr Model.
 *
 * Diese Klasse entfernt das doofie-Model wieder von allen normalen Items
 * (gleiche Sweep-Logik wie der LoreUpdater), damit sie ihre urspruengliche
 * Textur zurueckbekommen. Custom-Items erkennt man am PDC-Tag 'custom_item';
 * die werden nicht angefasst.
 */
public class ItemModelCleaner implements Listener {

    private final HardcorePlugin plugin;
    private final NamespacedKey customKey;

    public ItemModelCleaner(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.customKey = new NamespacedKey(plugin, "custom_item");
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (ItemStack item : player.getInventory().getContents()) {
                    clean(item);
                }
            }
        }, 40L, 40L);
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        clean(event.getItem().getItemStack());
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            clean(item);
        }
    }

    /** Entfernt gestempelte doofie-Models von Nicht-Custom-Items. */
    private void clean(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasItemModel()) return;
        if (!"doofie".equals(meta.getItemModel().getNamespace())) return;
        // Custom-Items (haben den custom_item-Tag) behalten ihr Model
        if (meta.getPersistentDataContainer().has(customKey, PersistentDataType.STRING)) return;
        meta.setItemModel(null);
        item.setItemMeta(meta);
    }
}
