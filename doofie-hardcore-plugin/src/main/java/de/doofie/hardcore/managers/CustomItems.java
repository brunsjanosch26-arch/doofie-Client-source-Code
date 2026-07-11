package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Custom-Items des Bounty SMP:
 *
 * — ELEMENT-RUESTUNGEN (nur per Admin-Command /doofieitem, volles 4er-Set noetig;
 *   aktive Faehigkeit jeweils per Rechtsklick mit leerer Hand):
 *   FEUER:  Schlaege zuenden Gegner an. Aktiv: Flammensturm — alle Gegner
 *           im 8er-Umkreis brennen 10s und nehmen 3 Herzen (10s Cooldown).
 *   WASSER: Wasseratmung + Conduit-Kraft, Nachtsicht unter Wasser. Aktiv:
 *           Gezeitenwelle — schleudert Gegner im 8er-Umkreis weg, 2 Herzen
 *           + Slowness III (10s Cooldown).
 *   ERDE:   Aktiv: Erdbloecke im 10x10-Bereich ballen sich zur Kanonenkugel
 *           ueber dir; nochmal Rechtsklick feuert sie ab (6 Herzen) —
 *           Auto-Aim/Homing auf den naechsten Mob, sonst Blickrichtung.
 *           Sneak+Rechtsklick toggelt den Bloecke-Essen-Modus: Rechtsklick
 *           verspeist jeden Block, Hungerkeulen je nach Verkaufswert.
 *           Rechtsklick mit beliebigem Schwert: ORBITAL STRIKE — eine
 *           riesige Scheibe aus ~260 explosiven Erd-Bomben (Ringe bis
 *           Radius 42) erscheint 50 Bloecke ueber dir, faellt herab und
 *           zuendet in Wellen wie der Unstable-SMP-Nuke-Shot (Schuetze
 *           immun, Krater fuellt sich wieder auf, 10 Min Cooldown).
 *   LUFT:   Schnelligkeit III + Sprungkraft III + kein Fallschaden. Aktiv:
 *           Windschub — katapultiert dich in Blickrichtung (5s Cooldown).
 *
 * — LEGENDAERER DOENER (craftbar, richtig teuer):
 *   Fuellt den Hunger komplett und gibt Eile II (15 Min), Staerke II (10 Min),
 *   Health Boost V = 20 Herzen (30 Min), Resistenz III (5 Min),
 *   Schnelligkeit III (15 Min) und Saettigung IV (5 Min).
 *
 * — GOETTERSPEER (craftbar, krank teuer):
 *   Netherite-Speer mit Sharpness VI, Lunge VI und Unbreakable. In der Hand:
 *   Staerke II, Schnelligkeit II, Resistenz I — und alle 10s trifft ein Blitz
 *   den naechsten feindlichen Mob oder Spieler im 20er-Umkreis bzw. im
 *   selben Chunk (5 Herzen).
 *
 * Jedes Item bekommt ein item_model (Namespace 'doofie'), damit es per
 * Resource Pack umtexturiert werden kann — siehe resourcepack/README.md.
 */
public class CustomItems implements Listener {

    private static final String ERDE_TAG = "doofie_erde";
    private static final String NUKE_TAG = "doofie_nuke";
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
    /** Goetterspeer: Blitz-Cooldown-Ende pro Spieler (ms) */
    private final Map<UUID, Long> blitzCooldown = new HashMap<>();
    /** Feuer/Wasser/Luft: Faehigkeits-Cooldown-Ende pro Spieler+Set (ms) */
    private final Map<String, Long> abilityCooldown = new HashMap<>();
    /** Erde-Set: Spieler mit aktivem Bloecke-Essen-Modus (Toggle per Sneak+Rechtsklick) */
    private final Set<UUID> erdeEssenModus = new HashSet<>();
    /** Orbital Strike: Ende der Explosions-Immunitaet des Schuetzen (ms) */
    private final Map<UUID, Long> nukeImmun = new HashMap<>();

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
                    "Volles Set: Schlaege zuenden Gegner an.",
                    "Rechtsklick (leere Hand): Flammensturm —",
                    "Gegner im 8er-Umkreis brennen (3 Herzen)."));
            case "wasser" -> unbreakableArmor(armorMaterial(teil), "wasser_" + teil,
                "Wasser-" + teilName(teil), NamedTextColor.BLUE,
                List.of("Teil der Wasser-Ruestung.",
                    "Volles Set: Wasseratmung, Conduit-Kraft,",
                    "Nachtsicht unter Wasser. Rechtsklick",
                    "(leere Hand): Gezeitenwelle — schleudert",
                    "Gegner weg (2 Herzen + Slowness III)."));
            case "erde" -> unbreakableArmor(armorMaterial(teil), "erde_" + teil,
                "Erd-" + teilName(teil), NamedTextColor.DARK_GREEN,
                List.of("Teil der Erd-Ruestung.",
                    "Volles Set: Rechtsklick mit leerer Hand",
                    "reisst Erdbloecke hoch (10x10) —",
                    "nochmal Rechtsklick feuert sie ab!",
                    "Auto-Aim auf den naechsten Mob, 6 Herzen.",
                    "Sneak+Rechtsklick: Bloecke-Essen-Modus —",
                    "iss jeden Block, Keulen nach Wert."));
            default -> unbreakableArmor(armorMaterial(teil), "luft_" + teil,
                "Luft-" + teilName(teil), NamedTextColor.WHITE,
                List.of("Teil der Luft-Ruestung.",
                    "Volles Set: Schnelligkeit III,",
                    "Sprungkraft III, kein Fallschaden.",
                    "Rechtsklick (leere Hand): Windschub —",
                    "katapultiert dich in Blickrichtung."));
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
                "Sharpness VI · Lunge VI · Unzerstoerbar",
                "In der Hand: Staerke II, Schnelligkeit II,",
                "Resistenz I — und Zeus blitzt alle 10s den",
                "naechsten Monster/Spieler im 20er-Umkreis",
                "oder im selben Chunk weg (5 Herzen)."));
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.SHARPNESS, 6, true);
        meta.addEnchant(Enchantment.LUNGE, 6, true);
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

        // Goetterspeer:
        //   Netherite-Barren | Nether-Stern     | Netherite-Barren
        //   Verz. Goldapfel  | Netherite-Speer  | Verz. Goldapfel
        //   Diamantblock     | Breeze-Rute      | Diamantblock
        ShapedRecipe speer = new ShapedRecipe(new NamespacedKey(plugin, "goetterspeer"), goetterspeer());
        speer.shape("BSB", "EPE", "DWD");
        speer.setIngredient('B', Material.NETHERITE_INGOT);
        speer.setIngredient('S', Material.NETHER_STAR);
        speer.setIngredient('E', Material.ENCHANTED_GOLDEN_APPLE);
        speer.setIngredient('P', Material.NETHERITE_SPEAR);
        speer.setIngredient('D', Material.DIAMOND_BLOCK);
        speer.setIngredient('W', Material.BREEZE_ROD);
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

            speerEffekte(p);
        }
    }

    // ────────────────────────── Goetterspeer: Buffs + Auto-Blitz ──────────────────────────

    /** Speer in Haupt- oder Nebenhand: permanente Buffs + Blitz auf den naechsten Gegner. */
    private void speerEffekte(Player p) {
        if (!is(p.getInventory().getItemInMainHand(), "goetterspeer")
            && !is(p.getInventory().getItemInOffHand(), "goetterspeer")) return;

        p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1, true, false, true));
        p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 0, true, false, true));

        // Auto-Blitz alle 10s: naechster feindlicher Mob oder Spieler im
        // 20er-Umkreis — oder im selben Chunk
        long jetzt = System.currentTimeMillis();
        Long cd = blitzCooldown.get(p.getUniqueId());
        if (cd != null && cd > jetzt) return;
        p.getWorld().getNearbyEntitiesByType(LivingEntity.class, p.getLocation(), 24).stream()
            .filter(e -> !e.equals(p))
            .filter(e -> e instanceof Monster || e instanceof Player)
            .filter(e -> e.getLocation().distanceSquared(p.getLocation()) <= 20 * 20
                || sameChunk(e.getLocation(), p.getLocation()))
            .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())))
            .ifPresent(ziel -> {
                p.getWorld().strikeLightningEffect(ziel.getLocation());
                ziel.damage(10.0, p); // 5 Herzen
                ziel.setFireTicks(60);
                blitzCooldown.put(p.getUniqueId(), jetzt + 10_000);
            });
    }

    private static boolean sameChunk(Location a, Location b) {
        return a.getWorld().equals(b.getWorld())
            && a.getBlockX() >> 4 == b.getBlockX() >> 4
            && a.getBlockZ() >> 4 == b.getBlockZ() >> 4;
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
        // Erd-Set + beliebiges Schwert (Holz bis Netherite): ORBITAL STRIKE
        if (p.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD")
            && fullSet(p, "erde")) {
            orbitalStrike(p);
            event.setCancelled(true);
            return;
        }

        if (!p.getInventory().getItemInMainHand().getType().isAir()) return; // nur leere Hand

        if (fullSet(p, "erde")) {
            if (p.isSneaking()) {
                // Sneak + Rechtsklick: Bloecke-Essen-Modus umschalten
                if (erdeEssenModus.remove(p.getUniqueId())) {
                    p.sendMessage(Component.text("Bloecke-Essen AUS — Rechtsklick laedt wieder die Kanonenkugel.",
                        NamedTextColor.DARK_GREEN));
                } else {
                    erdeEssenModus.add(p.getUniqueId());
                    p.sendMessage(Component.text("Bloecke-Essen AN — Rechtsklick auf einen Block verspeist ihn!",
                        NamedTextColor.DARK_GREEN));
                }
            } else if (erdeEssenModus.contains(p.getUniqueId())) {
                if (event.getClickedBlock() != null) erdblockEssen(p, event.getClickedBlock());
            } else {
                List<FallingBlock> geladen = erdeGeladen.get(p.getUniqueId());
                if (geladen != null) {
                    feuerErdbloeckeAb(p, geladen);
                } else {
                    ladeErdbloecke(p);
                }
            }
            event.setCancelled(true);
        } else if (fullSet(p, "feuer")) {
            feuersturm(p);
            event.setCancelled(true);
        } else if (fullSet(p, "wasser")) {
            gezeitenwelle(p);
            event.setCancelled(true);
        } else if (fullSet(p, "luft")) {
            windschub(p);
            event.setCancelled(true);
        }
    }

    // ────────────────────────── Aktive Set-Faehigkeiten (Rechtsklick, leere Hand) ──────────────────────────

    /** true, wenn die Faehigkeit bereit ist; setzt dann direkt den neuen Cooldown. */
    private boolean abilityBereit(Player p, String set, long cooldownMs, NamedTextColor farbe) {
        long jetzt = System.currentTimeMillis();
        Long cd = abilityCooldown.get(p.getUniqueId() + ":" + set);
        if (cd != null && cd > jetzt) {
            p.sendMessage(Component.text("Kraft laedt noch " + ((cd - jetzt) / 1000 + 1)
                + "s auf...", farbe));
            return false;
        }
        abilityCooldown.put(p.getUniqueId() + ":" + set, jetzt + cooldownMs);
        return true;
    }

    /** FEUER: Flammensturm — alle Gegner im 8er-Umkreis brennen und nehmen 3 Herzen. */
    private void feuersturm(Player p) {
        if (!abilityBereit(p, "feuer", 10_000, NamedTextColor.RED)) return;
        int getroffen = 0;
        for (LivingEntity ziel : p.getWorld().getNearbyEntitiesByType(LivingEntity.class, p.getLocation(), 8)) {
            if (ziel.equals(p)) continue;
            ziel.damage(6.0, p);
            ziel.setFireTicks(200); // 10 Sekunden
            getroffen++;
        }
        p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 300, 4, 1.5, 4, 0.05);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.7f);
        p.sendMessage(Component.text("Flammensturm! " + getroffen + " Gegner brennen.", NamedTextColor.RED));
    }

    /** WASSER: Gezeitenwelle — schleudert Gegner im 8er-Umkreis weg, 2 Herzen + Slowness III. */
    private void gezeitenwelle(Player p) {
        if (!abilityBereit(p, "wasser", 10_000, NamedTextColor.BLUE)) return;
        int getroffen = 0;
        for (LivingEntity ziel : p.getWorld().getNearbyEntitiesByType(LivingEntity.class, p.getLocation(), 8)) {
            if (ziel.equals(p)) continue;
            ziel.damage(4.0, p);
            ziel.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 2));
            Vector weg = ziel.getLocation().toVector().subtract(p.getLocation().toVector());
            if (weg.lengthSquared() < 0.01) weg = new Vector(1, 0, 0);
            ziel.setVelocity(weg.normalize().multiply(2.0).setY(0.6));
            getroffen++;
        }
        p.getWorld().spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1, 0), 500, 4, 1.5, 4, 0.1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 0.8f);
        p.sendMessage(Component.text("Gezeitenwelle! " + getroffen + " Gegner weggespuelt.", NamedTextColor.BLUE));
    }

    /** LUFT: Windschub — katapultiert dich in Blickrichtung (kein Fallschaden dank Set). */
    private void windschub(Player p) {
        if (!abilityBereit(p, "luft", 5_000, NamedTextColor.WHITE)) return;
        p.setVelocity(p.getEyeLocation().getDirection().clone().multiply(2.5).add(new Vector(0, 0.6, 0)));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 60, 0.5, 0.3, 0.5, 0.1);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.2f);
        p.sendMessage(Component.text("Windschub!", NamedTextColor.WHITE));
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

        // Nach dem Hochreissen zur Kanonenkugel ueber dem Spieler zusammenballen
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Vector> offsets = kugelOffsets(bloecke.size());
            Location zentrum = p.getEyeLocation().add(0, 3, 0);
            int i = 0;
            for (FallingBlock fb : bloecke) {
                if (!fb.isValid()) continue;
                fb.setGravity(false);
                fb.setVelocity(new Vector(0, 0, 0));
                fb.teleport(zentrum.clone().add(offsets.get(i++)));
            }
        }, 12L);

        erdeGeladen.put(p.getUniqueId(), bloecke);
        p.sendMessage(Component.text("Erd-Kanonenkugel (" + bloecke.size()
            + " Bloecke) schwebt ueber dir — Rechtsklick zum Abfeuern!",
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

    /** Erde-Set: angeklickten Block essen — Hungerkeulen je nach Verkaufswert. */
    private void erdblockEssen(Player p, Block b) {
        if (p.getFoodLevel() >= 20) {
            p.sendMessage(Component.text("Du bist pappsatt!", NamedTextColor.DARK_GREEN));
            return;
        }
        double preis = plugin.priceOf(b.getType());
        if (!b.getType().isItem() || preis <= 0) {
            p.sendMessage(Component.text("Das ist selbst fuer dich ungeniessbar.", NamedTextColor.DARK_GREEN));
            return;
        }
        // Probe-BlockBreakEvent: geschuetzte Bloecke (z.B. Shop-Kisten) sind tabu
        BlockBreakEvent probe = new BlockBreakEvent(b, p);
        Bukkit.getPluginManager().callEvent(probe);
        if (probe.isCancelled()) return;

        // 1 Keule Basis, +2 pro Zehnerpotenz Wert: 1$ = 1, 10$ = 3, 100$ = 5, 1000$ = 7 Keulen
        int keulen = Math.min(10, 1 + (int) (Math.log10(Math.max(1, preis)) * 2));
        p.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5),
            30, 0.3, 0.3, 0.3, b.getBlockData());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_BURP, 1f, 1f);
        b.setType(Material.AIR);
        p.setFoodLevel(Math.min(20, p.getFoodLevel() + keulen * 2));
        p.setSaturation(Math.min(p.getFoodLevel(), p.getSaturation() + keulen));
        p.sendMessage(Component.text("Mahlzeit! +" + keulen + (keulen == 1 ? " Hungerkeule" : " Hungerkeulen")
            + " (Wert: " + Math.round(preis) + "$)", NamedTextColor.DARK_GREEN));
    }

    /** Offsets fuer eine dichte Kugel-Formation: von innen nach aussen gefuellt. */
    private static List<Vector> kugelOffsets(int anzahl) {
        List<Vector> alle = new ArrayList<>();
        for (int x = -2; x <= 2; x++)
            for (int y = -2; y <= 2; y++)
                for (int z = -2; z <= 2; z++)
                    alle.add(new Vector(x, y, z));
        alle.sort(Comparator.comparingDouble(Vector::lengthSquared));
        return alle.subList(0, Math.min(anzahl, alle.size()));
    }

    /** Abgefeuerte/schwebende Erdbloecke duerfen nicht als Block landen. */
    @EventHandler
    public void onErdblockLandet(EntityChangeBlockEvent event) {
        // Orbital-Strike-Bombe: beim Aufschlag explodieren statt landen
        if (event.getEntity().getScoreboardTags().contains(NUKE_TAG)) {
            event.setCancelled(true);
            Location loc = event.getEntity().getLocation();
            event.getEntity().remove();
            loc.getWorld().createExplosion(loc, 4f, false, true);
            return;
        }
        if (!event.getEntity().getScoreboardTags().contains(ERDE_TAG)) return;
        event.setCancelled(true);
        event.getEntity().remove();
    }

    // ────────────────────────── Erde: Orbital Strike (Nuke Shot) ──────────────────────────

    /**
     * Rechtsklick mit beliebigem Schwert + volles Erd-Set: Orbital Strike auf
     * die eigene Position — 10 Schichten explosive Erde regnen vom Himmel
     * (wie der Orbital-Strike-Cannon-Nuke-Shot, nur mit Erde statt TNT).
     * Der Schuetze ist immun, der Krater fuellt sich danach mit Erde auf.
     */
    private void orbitalStrike(Player p) {
        if (!abilityBereit(p, "orbital", 600_000, NamedTextColor.DARK_GREEN)) return; // 10 Min
        Location zentrum = p.getLocation();
        var welt = zentrum.getWorld();

        // Alte Bodenhoehen im Umkreis merken, um den Krater spaeter aufzufuellen
        int radius = 48;
        int[][] alteHoehen = new int[radius * 2 + 1][radius * 2 + 1];
        for (int dx = -radius; dx <= radius; dx++)
            for (int dz = -radius; dz <= radius; dz++)
                alteHoehen[dx + radius][dz + radius] =
                    welt.getHighestBlockYAt(zentrum.getBlockX() + dx, zentrum.getBlockZ() + dz);

        // Explosions-Immunitaet fuer den Schuetzen, solange der Regen faellt
        nukeImmun.put(p.getUniqueId(), System.currentTimeMillis() + 45_000);

        welt.playSound(zentrum, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 4f, 0.5f);
        p.sendMessage(Component.text("ORBITAL STRIKE! In Deckung — die Erde kommt von oben.",
            NamedTextColor.DARK_GREEN));

        // Original-Nuke-Shot: Die Bomben erscheinen 50 Bloecke ueber dem Ziel
        // als riesige FLACHE SCHEIBE aus konzentrischen Ringen (bis Radius 42),
        // fallen senkrecht als geschlossene Scheibe herab und zuenden per
        // Zuendschnur (~4s) — teils noch in der Luft, in Wellen von innen
        // nach aussen. Ringe wie im Original: ~45 innen + 5x ~44 aussen.
        double[] ringRadien = {3, 8, 15, 25, 35, 42};
        int[] ringAnzahl = {45, 40, 44, 44, 44, 44};
        for (int ring = 0; ring < ringRadien.length; ring++) {
            final double bandRadius = ringRadien[ring];
            final int anzahl = ringAnzahl[ring];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int i = 0; i < anzahl; i++) {
                    double winkel = 2 * Math.PI * i / anzahl + Math.random() * 0.3;
                    double abstand = bandRadius + (Math.random() - 0.5) * 4;
                    Location spawn = zentrum.clone().add(
                        Math.cos(winkel) * abstand, 50, Math.sin(winkel) * abstand);
                    FallingBlock bombe = welt.spawnFallingBlock(spawn, Material.DIRT.createBlockData());
                    bombe.setDropItem(false);
                    bombe.setHurtEntities(false);
                    bombe.addScoreboardTag(NUKE_TAG);
                    bombe.setVelocity(new Vector(0, -0.5, 0));
                    // Zuendschnur wie TNT: nach ~4s zuenden, egal ob gelandet
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!bombe.isValid()) return;
                        Location loc = bombe.getLocation();
                        bombe.remove();
                        welt.createExplosion(loc, 4f, false, true);
                    }, 70L + (long) (Math.random() * 20));
                }
            }, ring * 4L);
        }

        // Nach 25s: Krater bis zur alten Bodenhoehe mit Erde auffuellen (gebatcht)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            List<Block> zuFuellen = new ArrayList<>();
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int altY = alteHoehen[dx + radius][dz + radius];
                    for (int y = altY; y > altY - 40; y--) {
                        Block b = welt.getBlockAt(zentrum.getBlockX() + dx, y, zentrum.getBlockZ() + dz);
                        if (!b.getType().isAir()) break; // erster fester Block: Saeule fertig
                        zuFuellen.add(b);
                    }
                }
            }
            // 400 Bloecke pro Tick setzen, damit nichts ruckelt
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                int budget = 400;
                while (budget-- > 0 && !zuFuellen.isEmpty()) {
                    zuFuellen.remove(zuFuellen.size() - 1).setType(Material.DIRT);
                }
                if (zuFuellen.isEmpty()) task.cancel();
            }, 1L, 1L);
        }, 20L * 25);
    }

    /** Der Orbital-Strike-Schuetze ist gegen die eigenen Explosionen immun. */
    @EventHandler
    public void onNukeSchaden(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
            && event.getCause() != EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) return;
        Long ende = nukeImmun.get(p.getUniqueId());
        if (ende != null && ende > System.currentTimeMillis()) event.setCancelled(true);
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
