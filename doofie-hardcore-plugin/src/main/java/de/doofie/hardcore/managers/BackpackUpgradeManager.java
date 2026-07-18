package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/**
 * Rucksack-Upgrades im Crafting-Rezeptbuch:
 * Stufe 1 + 8x Gold = Stufe 2 — Stufe 2 + 8x Eisen = Stufe 3 — Stufe 3 + 8x Diamant = Stufe 4.
 * Der Inhalt bleibt erhalten (gleiche Backpack-UUID, nur das Tier wird angehoben).
 */
public class BackpackUpgradeManager implements Listener {

    private static final NamespacedKey TIER_KEY = new NamespacedKey("backpackplus", "backpack_tier");

    private record Upgrade(int fromTier, Material ingredient, int modelId, String displayName) {}

    private static final Map<Integer, Upgrade> UPGRADES = Map.of(
            2, new Upgrade(1, Material.GOLD_INGOT, 800002, "<#E0B715>Golden Backpack"),
            3, new Upgrade(2, Material.IRON_INGOT, 800003, "<#00C2C6>Diamond Backpack"),
            4, new Upgrade(3, Material.DIAMOND, 800003, "<gradient:#FD5959:#545eb6>Doofie Backpack</gradient>"));

    private final HardcorePlugin plugin;

    public BackpackUpgradeManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        registerRecipes();
    }

    private NamespacedKey recipeKey(int toTier) {
        return new NamespacedKey(plugin, "rucksack_upgrade_" + toTier);
    }

    private void registerRecipes() {
        for (Map.Entry<Integer, Upgrade> e : UPGRADES.entrySet()) {
            int toTier = e.getKey();
            Upgrade up = e.getValue();
            ItemStack placeholder = new ItemStack(Material.BROWN_DYE);
            ItemMeta meta = placeholder.getItemMeta();
            meta.displayName(MiniMessage.miniMessage().deserialize(up.displayName()));
            meta.setCustomModelData(up.modelId());
            placeholder.setItemMeta(meta);

            ShapedRecipe recipe = new ShapedRecipe(recipeKey(toTier), placeholder);
            recipe.shape("III", "IBI", "III");
            recipe.setIngredient('I', up.ingredient());
            recipe.setIngredient('B', new RecipeChoice.MaterialChoice(Material.BROWN_DYE));
            plugin.getServer().addRecipe(recipe);
        }
        plugin.getLogger().info("Rucksack-Upgrade-Rezepte registriert (Stufe 2-4).");
    }

    /** Tier aus dem Item lesen — BackpackPlus speichert INTEGER oder STRING. */
    private int tierOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return -1;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        Integer i = pdc.get(TIER_KEY, PersistentDataType.INTEGER);
        if (i != null) return i;
        String s = pdc.get(TIER_KEY, PersistentDataType.STRING);
        if (s != null) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return -1;
    }

    private void setTier(ItemMeta meta, int tier) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(TIER_KEY, PersistentDataType.INTEGER)) {
            pdc.set(TIER_KEY, PersistentDataType.INTEGER, tier);
        } else {
            pdc.set(TIER_KEY, PersistentDataType.STRING, String.valueOf(tier));
        }
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!(event.getRecipe() instanceof ShapedRecipe shaped)) return;
        NamespacedKey key = shaped.getKey();
        if (!key.getNamespace().equals(plugin.getName().toLowerCase())) return;
        Integer toTier = null;
        for (int t : UPGRADES.keySet()) {
            if (key.equals(recipeKey(t))) { toTier = t; break; }
        }
        if (toTier == null) return;

        ItemStack center = event.getInventory().getMatrix().length > 4
                ? event.getInventory().getMatrix()[4] : null;
        Upgrade up = UPGRADES.get(toTier);
        int currentTier = tierOf(center);
        if (currentTier != up.fromTier()) {
            event.getInventory().setResult(null);
            return;
        }
        // Upgrade: Klon mit angehobenem Tier — UUID (und damit Inhalt) bleibt gleich
        ItemStack result = center.clone();
        result.setAmount(1);
        ItemMeta meta = result.getItemMeta();
        setTier(meta, toTier);
        meta.setCustomModelData(up.modelId());
        meta.displayName(MiniMessage.miniMessage().deserialize(up.displayName())
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        result.setItemMeta(meta);
        event.getInventory().setResult(result);
    }
}
