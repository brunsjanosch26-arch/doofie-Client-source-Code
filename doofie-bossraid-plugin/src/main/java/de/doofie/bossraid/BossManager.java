package de.doofie.bossraid;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Welt-Boss-Spawner, Schadens-Ranking und die vier Custom-Items.
 */
public class BossManager implements Listener, TabExecutor {

    private static final long INTERVALL_TICKS = 20L * 60 * 120; // 2 Stunden
    private static final List<EntityType> BOSS_TYPEN = List.of(
        EntityType.WITHER, EntityType.WARDEN, EntityType.RAVAGER);
    private static final List<String> BOSS_NAMEN = List.of(
        "Knochenfuerst", "Tiefenwaechter", "Sturmbestie");

    private final BossraidPlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey bossKey;
    /** Aktueller Boss (null = keiner aktiv) */
    private LivingEntity boss;
    /** Schaden pro Spieler am aktuellen Boss */
    private final Map<UUID, Double> schaden = new HashMap<>();
    /** Raid-Horn-Cooldowns */
    private final Map<UUID, Long> hornCooldown = new HashMap<>();
    /** Ahnen-Schild-Cooldowns */
    private final Map<UUID, Long> schildCooldown = new HashMap<>();

    public BossManager(BossraidPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
        this.bossKey = new NamespacedKey(plugin, "raid_boss");
    }

    public void start() {
        // Alle 2 Stunden ein Boss (erster nach 10 Minuten Serverlaufzeit)
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawneBoss, 20L * 600, INTERVALL_TICKS);

        // Seelenklinge: 5 Splitter im Kreuz + Blaze-Rute als Griff
        ShapedRecipe klinge = new ShapedRecipe(new NamespacedKey(plugin, "seelenklinge"), seelenklinge());
        klinge.shape(" S ", "SSS", " B ");
        klinge.setIngredient('S', new RecipeChoice.ExactChoice(seelensplitter()));
        klinge.setIngredient('B', Material.BLAZE_ROD);
        Bukkit.addRecipe(klinge);

        // Schild der Ahnen: Schild + 2 Splitter + 2 Netherite-Barren
        ShapedRecipe schild = new ShapedRecipe(new NamespacedKey(plugin, "ahnen_schild"), ahnenSchild());
        schild.shape("NSN", " H ", " S ");
        schild.setIngredient('N', Material.NETHERITE_INGOT);
        schild.setIngredient('S', new RecipeChoice.ExactChoice(seelensplitter()));
        schild.setIngredient('H', Material.SHIELD);
        Bukkit.addRecipe(schild);
    }

    // ────────────────────────── Boss ──────────────────────────

    public boolean spawneBoss() {
        if (boss != null && boss.isValid() && !boss.isDead()) return false;
        schaden.clear();
        World w = Bukkit.getWorlds().get(0);
        int i = ThreadLocalRandom.current().nextInt(BOSS_TYPEN.size());
        Location spawn = w.getSpawnLocation().clone().add(
            ThreadLocalRandom.current().nextInt(-60, 61), 0,
            ThreadLocalRandom.current().nextInt(-60, 61));
        spawn.setY(w.getHighestBlockYAt(spawn) + 1);

        boss = (LivingEntity) w.spawnEntity(spawn, BOSS_TYPEN.get(i));
        boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(600.0); // 300 Herzen
        boss.setHealth(600.0);
        boss.setGlowing(true);
        boss.setRemoveWhenFarAway(false);
        boss.customName(Component.text("☠ " + BOSS_NAMEN.get(i) + " ☠", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        boss.setCustomNameVisible(true);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);

        Bukkit.broadcast(Component.text("☠ WELT-BOSS! ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
            .append(Component.text(BOSS_NAMEN.get(i) + " ist bei "
                + spawn.getBlockX() + " / " + spawn.getBlockZ()
                + " erschienen — wer macht den meisten Schaden?", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, false)));
        w.playSound(spawn, Sound.ENTITY_WITHER_SPAWN, 4f, 0.7f);
        return true;
    }

    private boolean istBoss(org.bukkit.entity.Entity e) {
        return e.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE);
    }

    /** Schaden am Boss tracken — Seelenklinge macht +50%. */
    @EventHandler
    public void onBossHit(EntityDamageByEntityEvent event) {
        if (!istBoss(event.getEntity())) {
            // Umgekehrt: Boss trifft Spieler mit Ahnen-Schild?
            if (event.getDamager() instanceof LivingEntity le && istBoss(le)
                && event.getEntity() instanceof Player p
                && "ahnen_schild".equals(idOf(p.getInventory().getItemInOffHand()))) {
                long jetzt = System.currentTimeMillis();
                Long cd = schildCooldown.get(p.getUniqueId());
                if (cd == null || cd <= jetzt) {
                    schildCooldown.put(p.getUniqueId(), jetzt + 60_000);
                    event.setCancelled(true);
                    p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
                    p.sendMessage(Component.text("Der Schild der Ahnen hat den Treffer geschluckt! (60s Cooldown)",
                        NamedTextColor.AQUA));
                }
            }
            return;
        }
        if (!(event.getDamager() instanceof Player p)) return;
        double dmg = event.getFinalDamage();
        if ("seelenklinge".equals(idOf(p.getInventory().getItemInMainHand()))) {
            dmg *= 1.5;
            event.setDamage(event.getDamage() * 1.5);
        }
        schaden.merge(p.getUniqueId(), dmg, Double::sum);
    }

    /** Boss-Tod: Top-3 belohnen. */
    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!istBoss(event.getEntity())) return;
        boss = null;
        event.getDrops().clear();
        event.setDroppedExp(500);

        List<Map.Entry<UUID, Double>> top = schaden.entrySet().stream()
            .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
            .limit(3).toList();
        Bukkit.broadcast(Component.text("☠ Der Boss ist GEFALLEN! Bestenliste:", NamedTextColor.GOLD));
        int[] splitterProPlatz = {3, 2, 1};
        int[] diamantenProPlatz = {16, 8, 4};
        for (int platz = 0; platz < top.size(); platz++) {
            UUID uuid = top.get(platz).getKey();
            Player p = Bukkit.getPlayer(uuid);
            String name = String.valueOf(Bukkit.getOfflinePlayer(uuid).getName());
            Bukkit.broadcast(Component.text(" " + (platz + 1) + ". " + name + " — "
                + Math.round(top.get(platz).getValue()) + " Schaden", NamedTextColor.YELLOW));
            if (p != null) {
                ItemStack splitter = seelensplitter();
                splitter.setAmount(splitterProPlatz[platz]);
                p.getInventory().addItem(splitter, new ItemStack(Material.DIAMOND, diamantenProPlatz[platz]))
                    .values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }
        schaden.clear();
    }

    // ────────────────────────── Items ──────────────────────────

    private ItemStack tagged(Material mat, String id, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("bossraid", id));
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

    public ItemStack raidHorn() {
        return tagged(Material.GOAT_HORN, "raid_horn", "Raid-Horn", NamedTextColor.GOLD,
            List.of("Rechtsklick: beschwoert den Welt-Boss", "SOFORT (1 Stunde Cooldown)."));
    }

    public ItemStack seelensplitter() {
        return tagged(Material.ECHO_SHARD, "seelensplitter", "Seelensplitter", NamedTextColor.DARK_AQUA,
            List.of("Ein Splitter der Boss-Seele.", "5 Stueck + Blaze-Rute = Seelenklinge."));
    }

    public ItemStack seelenklinge() {
        ItemStack item = tagged(Material.NETHERITE_SWORD, "seelenklinge", "Seelenklinge", NamedTextColor.DARK_AQUA,
            List.of("Geschmiedet aus Boss-Seelen.",
                "Schaerfe VII · Fire Aspect II · Unzerstoerbar",
                "Macht +50% Schaden am Welt-Boss."));
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 7, true);
        meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack ahnenSchild() {
        ItemStack item = tagged(Material.SHIELD, "ahnen_schild", "Schild der Ahnen", NamedTextColor.AQUA,
            List.of("In der Nebenhand: blockt einen",
                "Boss-Treffer KOMPLETT (60s Cooldown)."));
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack byId(String id) {
        return switch (id) {
            case "raid_horn" -> raidHorn();
            case "seelensplitter" -> seelensplitter();
            case "seelenklinge" -> seelenklinge();
            case "ahnen_schild" -> ahnenSchild();
            default -> null;
        };
    }

    /** Raid-Horn per Rechtsklick. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!"raid_horn".equals(idOf(event.getItem()))) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        long jetzt = System.currentTimeMillis();
        Long cd = hornCooldown.get(p.getUniqueId());
        if (cd != null && cd > jetzt) {
            p.sendMessage(Component.text("Das Horn ist heiser — noch " + ((cd - jetzt) / 60_000 + 1)
                + " Minuten.", NamedTextColor.GOLD));
            return;
        }
        if (spawneBoss()) {
            hornCooldown.put(p.getUniqueId(), jetzt + 3_600_000);
            p.getWorld().playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 4f, 1f);
        } else {
            p.sendMessage(Component.text("Es ist schon ein Boss unterwegs!", NamedTextColor.RED));
        }
    }

    // ────────────────────────── /raid ──────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status" -> {
                if (boss != null && boss.isValid() && !boss.isDead()) {
                    sender.sendMessage(Component.text("☠ Boss aktiv bei "
                        + boss.getLocation().getBlockX() + " / " + boss.getLocation().getBlockZ()
                        + " — " + Math.round(boss.getHealth() / 2) + " Herzen uebrig.", NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("Kein Boss aktiv — alle 2h kommt einer (oder Raid-Horn).",
                        NamedTextColor.GRAY));
                }
            }
            case "top" -> {
                sender.sendMessage(Component.text("☠ Aktueller Schaden:", NamedTextColor.GOLD));
                schaden.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).limit(5)
                    .forEach(e -> sender.sendMessage(Component.text(" — "
                        + Bukkit.getOfflinePlayer(e.getKey()).getName() + ": "
                        + Math.round(e.getValue()), NamedTextColor.YELLOW)));
            }
            case "item" -> {
                if (!sender.hasPermission("doofie.bossraid.admin") || !(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Nur fuer Admins.", NamedTextColor.RED));
                    return true;
                }
                ItemStack item = args.length > 1 ? byId(args[1].toLowerCase(Locale.ROOT)) : null;
                if (item == null) {
                    sender.sendMessage(Component.text(
                        "Nutzung: /raid item <raid_horn|seelensplitter|seelenklinge|ahnen_schild>",
                        NamedTextColor.RED));
                    return true;
                }
                p.getInventory().addItem(item);
            }
            default -> sender.sendMessage(Component.text("Nutzung: /raid [status|top|item]", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) return List.of("status", "top", "item").stream()
            .filter(s -> s.startsWith(prefix)).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("item"))
            return List.of("raid_horn", "seelensplitter", "seelenklinge", "ahnen_schild").stream()
                .filter(s -> s.startsWith(prefix)).toList();
        return List.of();
    }
}
