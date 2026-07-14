package de.doofie.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Die Skyblock-Custom-Items (alle mit item_model 'skyblock:<id>'):
 *
 * — INSEL-KOMPASS: Rechtsklick = zurueck zum Spawn (3s Warmup).
 * — VOID-ANGEL: Rechtsklick = zufaellige Beute aus der Leere (30s CD).
 * — KERN-BRECHER: Netherite-Spitzhacke mit Effizienz X, unzerstoerbar.
 *
 * Plus /insel [home] — zurueck zur Spawn-Plattform.
 */
public class IslandManager implements Listener, TabExecutor {

    private final SkyblockPlugin plugin;
    private final NamespacedKey itemKey;
    private final Map<UUID, Long> angelCooldown = new HashMap<>();
    private final Map<UUID, Long> kompassCooldown = new HashMap<>();

    public IslandManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
    }

    public void start() {
        // Rezept: Kern-Brecher — Netherite-Spitzhacke + 4 Diamantbloecke + 4 Obsidian
        var brecher = new org.bukkit.inventory.ShapedRecipe(
            new NamespacedKey(plugin, "kern_brecher"), kernBrecher());
        brecher.shape("ODO", "DPD", "ODO");
        brecher.setIngredient('O', Material.OBSIDIAN);
        brecher.setIngredient('D', Material.DIAMOND_BLOCK);
        brecher.setIngredient('P', Material.NETHERITE_PICKAXE);
        Bukkit.addRecipe(brecher);
    }

    // ────────────────────────── Items ──────────────────────────

    private ItemStack tagged(Material mat, String id, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("skyblock", id));
        meta.displayName(Component.text(name, color, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
            .map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            .toList());
        item.setItemMeta(meta);
        return item;
    }

    private String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public ItemStack inselKompass() {
        return tagged(Material.COMPASS, "insel_kompass", "Insel-Kompass", NamedTextColor.AQUA,
            List.of("Rechtsklick: zurueck zum Spawn", "(3 Sekunden Warmup)."));
    }

    public ItemStack voidAngel() {
        return tagged(Material.FISHING_ROD, "void_angel", "Void-Angel", NamedTextColor.LIGHT_PURPLE,
            List.of("Rechtsklick: fischt zufaellige Beute", "aus der Leere (30s Cooldown)."));
    }

    public ItemStack kernBrecher() {
        ItemStack item = tagged(Material.NETHERITE_PICKAXE, "kern_brecher", "Kern-Brecher", NamedTextColor.DARK_RED,
            List.of("Frisst sich durch alles:", "Effizienz X, unzerstoerbar."));
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.EFFICIENCY, 10, true);
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    // ────────────────────────── Rechtsklick-Items ──────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = idOf(event.getItem());
        if (id == null) return;
        Player p = event.getPlayer();
        long jetzt = System.currentTimeMillis();

        if (id.equals("insel_kompass")) {
            event.setCancelled(true);
            Long cd = kompassCooldown.get(p.getUniqueId());
            if (cd != null && cd > jetzt) return;
            kompassCooldown.put(p.getUniqueId(), jetzt + 5_000);
            p.sendMessage(Component.text("Heimreise in 3 Sekunden — nicht bewegen!", NamedTextColor.AQUA));
            Location start = p.getLocation().clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (p.getLocation().distanceSquared(start) > 1.0) {
                    p.sendMessage(Component.text("Abgebrochen — du hast dich bewegt!", NamedTextColor.RED));
                    return;
                }
                p.teleport(plugin.karte().spawn());
            }, 60L);
        }

        if (id.equals("void_angel")) {
            event.setCancelled(true);
            Long cd = angelCooldown.get(p.getUniqueId());
            if (cd != null && cd > jetzt) {
                p.sendMessage(Component.text("Die Leere braucht noch " + ((cd - jetzt) / 1000 + 1) + "s.",
                    NamedTextColor.LIGHT_PURPLE));
                return;
            }
            angelCooldown.put(p.getUniqueId(), jetzt + 30_000);
            List<ItemStack> beute = List.of(
                new ItemStack(Material.COBBLESTONE, 16), new ItemStack(Material.IRON_INGOT, 3),
                new ItemStack(Material.OAK_LOG, 8), new ItemStack(Material.SAND, 8),
                new ItemStack(Material.DIAMOND), new ItemStack(Material.ENDER_PEARL, 2),
                new ItemStack(Material.GOLD_INGOT, 2), new ItemStack(Material.BONE_MEAL, 8));
            ItemStack fang = beute.get(ThreadLocalRandom.current().nextInt(beute.size())).clone();
            p.getInventory().addItem(fang).values()
                .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 0.6f);
            p.sendMessage(Component.text("Die Leere gibt her: " + fang.getAmount() + "x "
                + fang.getType().name().toLowerCase(Locale.ROOT), NamedTextColor.LIGHT_PURPLE));
        }
    }

    // ────────────────────────── /insel ──────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        p.teleport(plugin.karte().spawn());
        p.sendMessage(Component.text("Zurueck am Spawn — 10 Inseln rundherum!", NamedTextColor.AQUA));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        return List.of();
    }
}
