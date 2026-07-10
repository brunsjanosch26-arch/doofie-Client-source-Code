package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Custom-Items des Bounty SMP:
 *
 * — ELEMENT-RUESTUNGEN (nur per Admin-Command /doofieitem, volles 4er-Set noetig):
 *   FEUER:  Deine Schlaege zuenden Gegner 5 Sekunden an.
 *   WASSER: Wasseratmung + Conduit-Kraft, Nachtsicht unter Wasser.
 *   ERDE:   Rechtsklick mit leerer Hand reisst Erdbloecke im 10x10-Bereich
 *           hoch; nochmal Rechtsklick feuert sie ab (6 Herzen) — Auto-Aim
 *           auf den naechsten Mob, sonst exakt in Blickrichtung.
 *   LUFT:   Schnelligkeit III + Sprungkraft III + kein Fallschaden.
 *
 * — LEGENDAERER DOENER (craftbar, richtig teuer):
 *   Fuellt den Hunger komplett und gibt Eile II (15 Min), Staerke II (10 Min),
 *   Health Boost V = 20 Herzen (30 Min), Resistenz III (5 Min),
 *   Schnelligkeit III (15 Min) und Saettigung IV (5 Min).
 *
 * — GOETTERSPEER (craftbar, krank teuer):
 *   Netherite-Speer mit Sharpness VI und Unbreakable.
 *
 * Jedes Item bekommt ein item_model (Namespace 'doofie'), damit es per
 * Resource Pack umtexturiert werden kann — siehe resourcepack/README.md.
 */
public class CustomItems implements Listener {

    private static final String ERDE_TAG = "doofie_erde";
    private static final Set<Material> ERDBLOECKE = Set.of(
        Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT,
        Material.ROOTED_DIRT, Material.PODZOL, Material.MYCELIUM,
        Material.DIRT_PATH, Material.MUD, Material.FARMLAND);

    private final HardcorePlugin plugin;
    /** PDC-Tag: welches Custom-Item ist das? */
    private final NamespacedKey itemKey;
    /** Erde-Set: schwebende Bloecke pro Spieler (geladen, noch nicht abgefeuert) */
    private final Map<UUID, List<FallingBlock>> erdeGeladen = new HashMap<>();
    /** Erde-Set: Cooldown-Ende pro Spieler (ms) */
    private final Map<UUID, Long> erdeCooldown = new HashMap<>();

    public CustomItems(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
    }

    /** Startet den Effekt-Task und registriert die Crafting-Rezepte. */
    public void start() {
        registerRecipes();
        // Bereits online (Plugin-Reload): Rezepte sofort ins Rezeptbuch
        Bukkit.getOnlinePlayers().forEach(this::discoverRecipes);
        // Alle 3 Sekunden: Set-Boni pruefen (Effektdauer 5s, damit nichts flackert)
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyArmorEffects, 60L, 60L);
    }

    /** Schaltet die Custom-Rezepte im Rezeptbuch frei. */
    private void discoverRecipes(Player p) {
        p.discoverRecipe(new NamespacedKey(plugin, "doener"));
        p.discoverRecipe(new NamespacedKey(plugin, "goetterspeer"));
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
        // item_model fuer Resource-Pack-Texturen: doofie:<id>
        meta.setItemModel(new NamespacedKey("doofie", id));
        meta.displayName(Component.text(name, color, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
            .map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            .toList());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack unbreakableArmor(Material mat, String id, String name,
                                       NamedTextColor color, List<String> lore) {
        ItemStack item = tagged(mat, id, name, color, lore);
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        // Equipment-Asset doofie:<element>, damit das Resource Pack die
        // Ruestung auch AM KOERPER umtexturieren kann (statt Netherite-Optik)
        String element = id.split("_", 2)[0];
        EquippableComponent eq = meta.getEquippable();
        eq.setSlot(armorSlot(id.split("_", 2)[1]));
        eq.setModel(new NamespacedKey("doofie", element));
        meta.setEquippable(eq);
        item.setItemMeta(meta);
        return item;
    }

    private static EquipmentSlot armorSlot(String teil) {
        return switch (teil) {
            case "helm" -> EquipmentSlot.HEAD;
            case "brust" -> EquipmentSlot.CHEST;
            case "hose" -> EquipmentSlot.LEGS;
            default -> EquipmentSlot.FEET;
        };
    }

    private static Material armorMaterial(String teil) {
        return switch (teil) {
            case "helm" -> Material.NETHERITE_HELMET;
            case "brust" -> Material.NETHERITE_CHESTPLATE;
            case "hose" -> Material.NETHERITE_LEGGINGS;
            default -> Material.NETHERITE_BOOTS;
        };
    }

    private static String teilName(String teil) {
        return switch (teil) {
            case "helm" -> "Helm";
            case "brust" -> "Brustplatte";
            case "hose" -> "Beinschutz";
            default -> "Stiefel";
        };
    }

    /** Baut ein Element-Ruestungsteil, z.B. element="feuer", teil="helm". */
    private ItemStack elementPiece(String element, String teil) {
        return switch (element) {
            case "feuer" -> unbreakableArmor(armorMaterial(teil), "feuer_" + teil,
                "Feuer-" + teilName(teil), NamedTextColor.RED,
                List.of("Teil der Feuer-Ruestung.",
                    "Volles Set: Deine Schlaege zuenden", "Gegner 5 Sekunden an."));
            case "wasser" -> unbreakableArmor(armorMaterial(teil), "wasser_" + teil,
                "Wasser-" + teilName(teil), NamedTextColor.BLUE,
                List.of("Teil der Wasser-Ruestung.",
                    "Volles Set: Wasseratmung, Conduit-Kraft", "und Nachtsicht unter Wasser."));
            case "erde" -> unbreakableArmor(armorMaterial(teil), "erde_" + teil,
                "Erd-" + teilName(teil), NamedTextColor.DARK_GREEN,
                List.of("Teil der Erd-Ruestung.",
                    "Volles Set: Rechtsklick mit leerer Hand",
                    "reisst Erdbloecke hoch (10x10) —",
                    "nochmal Rechtsklick feuert sie ab!",
                    "Auto-Aim auf den naechsten Mob, 6 Herzen."));
            default -> unbreakableArmor(armorMaterial(teil), "luft_" + teil,
                "Luft-" + teilName(teil), NamedTextColor.WHITE,
                List.of("Teil der Luft-Ruestung.",
                    "Volles Set: Schnelligkeit III,", "Sprungkraft III, kein Fallschaden."));
        };
    }

    public ItemStack doener() {
        ItemStack item = tagged(Material.COOKED_BEEF, "doener",
            "Legendärer Döner", NamedTextColor.GOLD,
            List.of("Mit alles und scharf.",
                "Eile II (15m), Staerke II (10m),",
                "20 Herzen (30m), Resistenz III (5m),",
                "Schnelligkeit III (15m), Saettigung IV (5m)."));
        ItemMeta meta = item.getItemMeta();
        FoodComponent food = meta.getFood();
        food.setNutrition(20);
        food.setSaturation(20f);
        food.setCanAlwaysEat(true);
        meta.setFood(food);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack goetterspeer() {
        ItemStack item = tagged(Material.NETHERITE_SPEAR, "goetterspeer",
            "Götterspeer", NamedTextColor.LIGHT_PURPLE,
            List.of("Geschmiedet aus Sternenstaub und Groessenwahn.",
                "Sharpness VI · Unzerstoerbar"));
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 6, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Liefert das Custom-Item zu einer ID (z.B. "hermes_helm", "feuer_brust", "doener"). */
    public ItemStack byId(String id) {
        if (id.equals("doener")) return doener();
        if (id.equals("speer") || id.equals("goetterspeer")) return goetterspeer();
        String[] teile = id.split("_", 2);
        if (teile.length != 2) return null;
        String set = teile[0], teil = teile[1];
        if (!List.of("helm", "brust", "hose", "schuhe").contains(teil)) return null;
        return switch (set) {
            case "feuer", "wasser", "erde", "luft" -> elementPiece(set, teil);
            default -> null;
        };
    }

    /** Ist der Stack das angegebene Custom-Item? */
    private boolean is(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        return id.equals(item.getItemMeta().getPersistentDataContainer()
            .get(itemKey, PersistentDataType.STRING));
    }

    /** Traegt der Spieler das volle 4er-Set eines Elements (helm+brust+hose+schuhe)? */
    private boolean fullSet(Player p, String set) {
        PlayerInventory inv = p.getInventory();
        return is(inv.getHelmet(), set + "_helm")
            && is(inv.getChestplate(), set + "_brust")
            && is(inv.getLeggings(), set + "_hose")
            && is(inv.getBoots(), set + "_schuhe");
    }

    // ────────────────────────── Rezepte ──────────────────────────

    private void registerRecipes() {
        // Legendaerer Doener:
        //   Gold      | Diamant         | Gold
        //   Haehnchen | Brot            | Haehnchen
        //   Diamant   | Verz. Goldapfel | Diamant
        ShapedRecipe doener = new ShapedRecipe(new NamespacedKey(plugin, "doener"), doener());
        doener.shape("GDG", "CBC", "DAD");
        doener.setIngredient('G', Material.GOLD_INGOT);
        doener.setIngredient('D', Material.DIAMOND);
        doener.setIngredient('C', Material.COOKED_CHICKEN);
        doener.setIngredient('B', Material.BREAD);
        doener.setIngredient('A', Material.ENCHANTED_GOLDEN_APPLE);
        Bukkit.addRecipe(doener);

        // Goetterspeer — krank teuer (~30.000$ Materialwert)
        ShapedRecipe speer = new ShapedRecipe(new NamespacedKey(plugin, "goetterspeer"), goetterspeer());
        speer.shape("BSB", "EPE", "DWD");
        speer.setIngredient('B', Material.NETHERITE_BLOCK);
        speer.setIngredient('S', Material.NETHER_STAR);
        speer.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
        speer.setIngredient('P', Material.NETHERITE_SPEAR);
        speer.setIngredient('D', Material.DIAMOND_BLOCK);
        speer.setIngredient('W', Material.BEACON);
        Bukkit.addRecipe(speer);
    }

    // ────────────────────────── Passive Set-Effekte ──────────────────────────

    private void applyArmorEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (fullSet(p, "wasser")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.WATER_BREATHING, 100, 0, true, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 100, 0, true, false, true));
                if (p.isInWater()) {
                    // Nachtsicht laenger geben, damit sie nicht am Ende blinkt
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, true));
                }
            }

            if (fullSet(p, "luft")) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 2, true, false, true));
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, 2, true, false, true));
            }
        }
    }

    // ────────────────────────── Feuer: Schlaege zuenden an ──────────────────────────

    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player p)) return;
        if (!fullSet(p, "feuer")) return;
        event.getEntity().setFireTicks(100); // 5 Sekunden Feuer
    }

    // ────────────────────────── Luft: kein Fallschaden ──────────────────────────

    @EventHandler
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (fullSet(p, "luft")) event.setCancelled(true);
    }

    // ────────────────────────── Erde: Bloecke hochreissen + abfeuern ──────────────────────────

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!p.getInventory().getItemInMainHand().getType().isAir()) return; // nur leere Hand
        if (!fullSet(p, "erde")) return;

        List<FallingBlock> geladen = erdeGeladen.get(p.getUniqueId());
        if (geladen != null) {
            feuerErdbloeckeAb(p, geladen);
        } else {
            ladeErdbloecke(p);
        }
        event.setCancelled(true);
    }

    /** Phase 1: Erdbloecke im 10x10-Bereich aus dem Boden reissen, sie schweben. */
    private void ladeErdbloecke(Player p) {
        long jetzt = System.currentTimeMillis();
        Long cooldown = erdeCooldown.get(p.getUniqueId());
        if (cooldown != null && cooldown > jetzt) {
            p.sendMessage(Component.text("Erd-Kraft laedt noch " + ((cooldown - jetzt) / 1000 + 1)
                + "s auf...", NamedTextColor.DARK_GREEN));
            return;
        }

        List<FallingBlock> bloecke = new ArrayList<>();
        Location base = p.getLocation();
        for (int dx = -5; dx <= 5 && bloecke.size() < 40; dx++) {
            for (int dz = -5; dz <= 5 && bloecke.size() < 40; dz++) {
                for (int dy = 2; dy >= -3 && bloecke.size() < 40; dy--) {
                    Block b = base.clone().add(dx, dy, dz).getBlock();
                    if (!ERDBLOECKE.contains(b.getType())) continue;
                    FallingBlock fb = p.getWorld().spawnFallingBlock(
                        b.getLocation().add(0.5, 0, 0.5), b.getBlockData());
                    b.setType(Material.AIR);
                    fb.setDropItem(false);
                    fb.setHurtEntities(false);
                    fb.addScoreboardTag(ERDE_TAG);
                    fb.setVelocity(new Vector(0, 0.6, 0)); // hochreissen
                    bloecke.add(fb);
                    break; // pro Saeule nur der oberste Erdblock
                }
            }
        }
        if (bloecke.isEmpty()) {
            p.sendMessage(Component.text("Keine Erdbloecke in der Naehe!", NamedTextColor.DARK_GREEN));
            return;
        }

        // Nach dem Hochreissen schweben lassen
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (FallingBlock fb : bloecke) {
                if (!fb.isValid()) continue;
                fb.setGravity(false);
                fb.setVelocity(new Vector(0, 0, 0));
            }
        }, 12L);

        erdeGeladen.put(p.getUniqueId(), bloecke);
        p.sendMessage(Component.text(bloecke.size() + " Erdbloecke schweben — Rechtsklick zum Abfeuern!",
            NamedTextColor.DARK_GREEN));

        // Sicherheit: nach 30s ohne Abfeuern loesen sich die Bloecke auf
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (erdeGeladen.remove(p.getUniqueId(), bloecke)) {
                bloecke.forEach(fb -> { if (fb.isValid()) fb.remove(); });
            }
        }, 20L * 30);
    }

    /** Phase 2: Abfeuern — Auto-Aim auf den naechsten Mob, sonst exakt in Blickrichtung. */
    private void feuerErdbloeckeAb(Player p, List<FallingBlock> bloecke) {
        erdeGeladen.remove(p.getUniqueId());
        erdeCooldown.put(p.getUniqueId(), System.currentTimeMillis() + 10_000); // 10s Cooldown

        // Auto-Aim: naechster Mob im Umkreis von 30 Bloecken
        Mob autoZiel = p.getWorld().getNearbyEntitiesByType(Mob.class, p.getLocation(), 30).stream()
            .min(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(p.getLocation())))
            .orElse(null);

        List<FallingBlock> fliegend = new ArrayList<>();
        for (FallingBlock fb : bloecke) {
            if (!fb.isValid()) continue;
            fb.setGravity(false); // gerade Flugbahn — auch senkrecht nach oben oder geradeaus
            Vector richtung = autoZiel != null
                ? mitte(autoZiel).subtract(fb.getLocation().toVector())
                : p.getEyeLocation().getDirection().clone();
            fb.setVelocity(richtung.normalize().multiply(1.6));
            fliegend.add(fb);
        }
        p.sendMessage(autoZiel != null
            ? Component.text("Erdgeschosse jagen " + autoZiel.getName() + "!", NamedTextColor.DARK_GREEN)
            : Component.text("Erdgeschosse abgefeuert!", NamedTextColor.DARK_GREEN));

        // Treffer-Task: Zielverfolgung + Geschosse verletzen alles in ihrer Flugbahn (6 Herzen)
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            fliegend.removeIf(fb -> {
                if (!fb.isValid()) return true;
                // Homing: Kurs jeden Tick auf den Ziel-Mob korrigieren
                if (autoZiel != null && autoZiel.isValid() && !autoZiel.isDead()) {
                    fb.setVelocity(mitte(autoZiel).subtract(fb.getLocation().toVector())
                        .normalize().multiply(1.6));
                }
                for (var entity : fb.getNearbyEntities(1.2, 1.2, 1.2)) {
                    if (entity instanceof LivingEntity ziel2 && !ziel2.equals(p)) {
                        ziel2.damage(12.0, p);
                        fb.remove();
                        return true;
                    }
                }
                return false;
            });
            if (fliegend.isEmpty()) task.cancel();
        }, 1L, 1L);

        // Nach 5s Restgeschosse aufraeumen
        Bukkit.getScheduler().runTaskLater(plugin, () ->
            fliegend.forEach(fb -> { if (fb.isValid()) fb.remove(); }), 100L);
    }

    /** Koerpermitte eines Mobs als Zielpunkt. */
    private static Vector mitte(Mob m) {
        return m.getLocation().toVector().add(new Vector(0, m.getHeight() / 2, 0));
    }

    /** Abgefeuerte/schwebende Erdbloecke duerfen nicht als Block landen. */
    @EventHandler
    public void onErdblockLandet(EntityChangeBlockEvent event) {
        if (!event.getEntity().getScoreboardTags().contains(ERDE_TAG)) return;
        event.setCancelled(true);
        event.getEntity().remove();
    }

    // ────────────────────────── Doener-Buff ──────────────────────────

    @EventHandler
    public void onEat(PlayerItemConsumeEvent event) {
        if (!is(event.getItem(), "doener")) return;
        Player p = event.getPlayer();
        p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 20 * 900, 1));         // 15 Min Eile II
        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 600, 1));      // 10 Min Staerke II
        p.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, 20 * 1800, 4)); // 30 Min +10 Herzen = 20 gesamt
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 300, 2));    // 5 Min Resistenz III
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20 * 900, 2));         // 15 Min Schnelligkeit III
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 300, 3));    // 5 Min Saettigung IV
        // Bonus-Herzen direkt fuellen statt leer lassen
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        p.setHealth(Math.min(p.getHealth() + 20, max));
        p.sendMessage(Component.text("Mit alles und scharf — guten Appetit!", NamedTextColor.GOLD));
    }
}
