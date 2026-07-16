package de.doofie.lifesteal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Custom-Items des Doofie-Lifesteal:
 *
 * — HERZ (droppt beim Kill, craftbar, richtig teuer):
 *   Rechtsklick = +1 Herz (max 20). Rezept: 4 Herz-Fragmente +
 *   4 Diamantbloecke + 1 Netherite-Barren.
 *
 * — HERZ-FRAGMENT (craftbar):
 *   Zwischenprodukt. Rezept: 1 Totem der Unsterblichkeit umringt
 *   von 8 Redstone-Bloecken.
 *
 * — LIFESTEAL-SCHWERT (craftbar):
 *   Netherite-Schwert mit Schaerfe V, Unbreakable. Jeder Treffer auf
 *   Spieler oder Mobs heilt dich um 1 Herz.
 *   Rezept: Herz oben, Netherite-Schwert Mitte, Totem unten.
 *
 * Jedes Item bekommt ein item_model (Namespace 'lifesteal'), damit es per
 * Resource Pack umtexturiert werden kann — siehe resourcepack/README.md.
 */
public class LifestealItems implements Listener {

    public static final String HERZ = "herz";
    public static final String FRAGMENT = "herz_fragment";
    public static final String SCHWERT = "lifesteal_schwert";

    private final LifestealPlugin plugin;
    /** PDC-Tag: welches Custom-Item ist das? */
    private final NamespacedKey itemKey;

    public LifestealItems(LifestealPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
    }

    public void start() {
        registerRecipes();
        Bukkit.getOnlinePlayers().forEach(this::discoverRecipes);
    }

    private void discoverRecipes(Player p) {
        p.discoverRecipe(new NamespacedKey(plugin, HERZ));
        p.discoverRecipe(new NamespacedKey(plugin, FRAGMENT));
        p.discoverRecipe(new NamespacedKey(plugin, SCHWERT));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        discoverRecipes(event.getPlayer());
    }

    // ────────────────────────── Item-Fabrik ──────────────────────────

    private ItemStack tagged(Material mat, String id, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        // item_model fuer Resource-Pack-Texturen: lifesteal:<id>
        // (eigener Namespace, damit der ItemModelCleaner des Hardcore-Plugins
        //  — der fremde 'doofie:'-Models wegputzt — diese Items in Ruhe laesst)
        meta.setItemModel(new NamespacedKey("lifesteal", id));
        meta.displayName(Component.text(name, color, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
            .map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            .toList());
        item.setItemMeta(meta);
        return item;
    }

    /** Liefert die Custom-Item-ID oder null. */
    public String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public ItemStack herz() {
        return tagged(Material.RED_DYE, HERZ, "❤ Herz", NamedTextColor.RED, List.of(
            "Rechtsklick: +1 Herz (max " + HeartManager.MAX_HERZEN + ").",
            "Droppt, wenn ein Spieler getoetet wird —",
            "oder teuer craftbar."));
    }

    public ItemStack fragment() {
        return tagged(Material.GLISTERING_MELON_SLICE, FRAGMENT, "Herz-Fragment", NamedTextColor.LIGHT_PURPLE, List.of(
            "Ein Viertel eines Herzens.",
            "4 Fragmente + 4 Diamantbloecke +",
            "1 Netherite-Barren = 1 Herz."));
    }

    public ItemStack schwert() {
        ItemStack item = tagged(Material.NETHERITE_SWORD, SCHWERT, "Lifesteal-Schwert", NamedTextColor.DARK_RED, List.of(
            "Jeder Treffer heilt dich um 1 Herz.",
            "Schaerfe V, unzerstoerbar."));
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 5, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack byId(String id) {
        return switch (id) {
            case HERZ -> herz();
            case FRAGMENT -> fragment();
            case SCHWERT -> schwert();
            default -> null;
        };
    }

    // ────────────────────────── Rezepte ──────────────────────────

    private void registerRecipes() {
        // Herz-Fragment: Totem umringt von Redstone-Bloecken
        ShapedRecipe frag = new ShapedRecipe(new NamespacedKey(plugin, FRAGMENT), fragment());
        frag.shape("RRR", "RTR", "RRR");
        frag.setIngredient('R', Material.REDSTONE_BLOCK);
        frag.setIngredient('T', Material.TOTEM_OF_UNDYING);
        Bukkit.addRecipe(frag);

        // Herz: 4 Fragmente + 4 Diamantbloecke + Netherite-Barren
        ShapedRecipe herz = new ShapedRecipe(new NamespacedKey(plugin, HERZ), herz());
        herz.shape("FDF", "DND", "FDF");
        herz.setIngredient('F', new RecipeChoice.ExactChoice(fragment()));
        herz.setIngredient('D', Material.DIAMOND_BLOCK);
        herz.setIngredient('N', Material.NETHERITE_INGOT);
        Bukkit.addRecipe(herz);

        // Lifesteal-Schwert: Herz / Netherite-Schwert / Totem
        ShapedRecipe schwert = new ShapedRecipe(new NamespacedKey(plugin, SCHWERT), schwert());
        schwert.shape("H", "S", "T");
        schwert.setIngredient('H', new RecipeChoice.ExactChoice(herz()));
        schwert.setIngredient('S', Material.NETHERITE_SWORD);
        schwert.setIngredient('T', Material.TOTEM_OF_UNDYING);
        Bukkit.addRecipe(schwert);
    }

    // ────────────────────────── Herz einloesen ──────────────────────────

    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        String id = idOf(item);
        if (id == null) return;

        Player p = event.getPlayer();
        switch (id) {
            case HERZ -> {
                event.setCancelled(true);
                HeartManager hearts = plugin.getHeartManager();
                if (hearts.getHerzen(p.getUniqueId()) >= HeartManager.MAX_HERZEN) {
                    p.sendMessage(Component.text("Du hast schon das Maximum von "
                        + HeartManager.MAX_HERZEN + " Herzen!", NamedTextColor.RED));
                    return;
                }
                if (item.getAmount() <= 1) {
                    p.getInventory().setItemInMainHand(null);
                } else {
                    item.setAmount(item.getAmount() - 1);
                    p.getInventory().setItemInMainHand(item);
                }
                hearts.addHerz(p.getUniqueId());
                p.setHealth(Math.min(p.getHealth() + 2.0,
                    hearts.getHerzen(p.getUniqueId()) * 2.0));
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 8, 0.4, 0.4, 0.4);
                p.sendMessage(Component.text("❤ +1 Herz! Du hast jetzt "
                    + hearts.getHerzen(p.getUniqueId()) + " Herzen.", NamedTextColor.GREEN));
            }
            default -> { }
        }
    }

    // ────────────────────────── Lifesteal-Schwert ──────────────────────────

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (!SCHWERT.equals(idOf(p.getInventory().getItemInMainHand()))) return;
        double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(p.getHealth() + 2.0, max));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.8, 0), 3, 0.3, 0.2, 0.3);
    }
}
