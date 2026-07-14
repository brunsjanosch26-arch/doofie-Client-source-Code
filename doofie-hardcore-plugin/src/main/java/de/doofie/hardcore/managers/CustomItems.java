package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import io.papermc.paper.event.entity.EntityLungeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.FoodComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

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
 * — LEGENDAERER DOENER (craftbar, richtig teuer):
 *   Fuellt den Hunger komplett und gibt Eile II (15 Min), Staerke II (10 Min),
 *   Health Boost V = 20 Herzen (30 Min), Resistenz III (5 Min),
 *   Schnelligkeit III (15 Min) und Saettigung IV (5 Min).
 *
 * — GOETTERSPEER (craftbar, krank teuer — zwei Rezepte):
 *   Netherite-Speer mit Sharpness VI, Lunge VI und Unbreakable. In der Hand:
 *   Staerke II, Schnelligkeit II, Resistenz I — und alle 10s trifft ein Blitz
 *   den naechsten feindlichen Mob oder Spieler im 20er-Umkreis bzw. im
 *   selben Chunk (5 Herzen).
 *
 * — LUNGE OHNE HUNGER: Wer die Lunge-Attacke (Speer-Ansturm) benutzt,
 *   verliert dabei keinen Hunger mehr.
 *
 * (Die frueheren Element-Ruestungen Feuer/Wasser/Erde/Luft sind entfernt.)
 *
 * Jedes Item bekommt ein item_model (Namespace 'doofie'), damit es per
 * Resource Pack umtexturiert werden kann — siehe resourcepack/README.md.
 */
public class CustomItems implements Listener {

    private final HardcorePlugin plugin;
    /** PDC-Tag: welches Custom-Item ist das? */
    private final NamespacedKey itemKey;
    /** Goetterspeer: Blitz-Cooldown-Ende pro Spieler (ms) */
    private final Map<UUID, Long> blitzCooldown = new HashMap<>();
    /** God Mace: Spieler, deren Doppelsprung gerade bereit ist */
    private final Set<UUID> doppelsprungBereit = new HashSet<>();

    public CustomItems(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
    }

    /** Startet den Effekt-Task und registriert die Crafting-Rezepte. */
    public void start() {
        registerRecipes();
        // Bereits online (Plugin-Reload): Rezepte sofort ins Rezeptbuch
        Bukkit.getOnlinePlayers().forEach(this::discoverRecipes);
        // Alle 3 Sekunden: Speer-Buffs pruefen (Effektdauer 5s, damit nichts flackert)
        Bukkit.getScheduler().runTaskTimer(plugin, this::applyItemEffects, 60L, 60L);
        // Alle 10 Ticks: God-Mace-Doppelsprung scharf schalten
        Bukkit.getScheduler().runTaskTimer(plugin, this::maceFlugCheck, 10L, 10L);
    }

    /** Schaltet die Custom-Rezepte im Rezeptbuch frei. */
    private void discoverRecipes(Player p) {
        p.discoverRecipe(new NamespacedKey(plugin, "doener"));
        p.discoverRecipe(new NamespacedKey(plugin, "goetterspeer"));
        p.discoverRecipe(new NamespacedKey(plugin, "goetterspeer_koepfe"));
        p.discoverRecipe(new NamespacedKey(plugin, "god_mace"));
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

    public ItemStack godMace() {
        ItemStack item = tagged(Material.MACE, "god_mace",
            "God Mace", NamedTextColor.GOLD,
            List.of("Zwei Keulen, ein Speer, ein Beacon —",
                "und der Himmel bekommt Angst.",
                "Wucht VII · Durchbruch V · Windstoss III",
                "Unzerstoerbar."));
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(Enchantment.DENSITY, 7, true);
        meta.addEnchant(Enchantment.BREACH, 5, true);
        meta.addEnchant(Enchantment.WIND_BURST, 3, true);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Liefert das Custom-Item zu einer ID ("doener", "speer"/"goetterspeer", "god_mace"). */
    public ItemStack byId(String id) {
        if (id.equals("doener")) return doener();
        if (id.equals("speer") || id.equals("goetterspeer")) return goetterspeer();
        if (id.equals("god_mace") || id.equals("mace")) return godMace();
        return null;
    }

    /** Ist der Stack das angegebene Custom-Item? */
    private boolean is(ItemStack item, String id) {
        if (item == null || !item.hasItemMeta()) return false;
        return id.equals(item.getItemMeta().getPersistentDataContainer()
            .get(itemKey, PersistentDataType.STRING));
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

        // Goetterspeer (klassisch):
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

        // Goetterspeer (Koepfe-Rezept — Blutzoll statt Goldaepfel):
        //   Spielerkopf  | Spielerkopf     | Spielerkopf
        //   Goldblock    | Nether-Stern    | Goldblock
        //   Diamantblock | Netherite-Speer | Diamantblock
        ShapedRecipe koepfe = new ShapedRecipe(new NamespacedKey(plugin, "goetterspeer_koepfe"), goetterspeer());
        koepfe.shape("KKK", "GNG", "DPD");
        koepfe.setIngredient('K', Material.PLAYER_HEAD);
        koepfe.setIngredient('G', Material.GOLD_BLOCK);
        koepfe.setIngredient('N', Material.NETHER_STAR);
        koepfe.setIngredient('D', Material.DIAMOND_BLOCK);
        koepfe.setIngredient('P', Material.NETHERITE_SPEAR);
        Bukkit.addRecipe(koepfe);

        // God Mace:
        //   Streitkolben | Streitkolben
        //   Netherite-Speer | Beacon
        ShapedRecipe mace = new ShapedRecipe(new NamespacedKey(plugin, "god_mace"), godMace());
        mace.shape("MM", "SB");
        mace.setIngredient('M', Material.MACE);
        mace.setIngredient('S', Material.NETHERITE_SPEAR);
        mace.setIngredient('B', Material.BEACON);
        Bukkit.addRecipe(mace);
    }

    // ────────────────────────── Goetterspeer: Buffs + Auto-Blitz ──────────────────────────

    private void applyItemEffects() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            speerEffekte(p);
        }
    }

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

    // ────────────────────────── God Mace: Doppelsprung + kein Fallschaden ──────────────────────────

    private boolean traegtGodMace(Player p) {
        return is(p.getInventory().getItemInMainHand(), "god_mace")
            || is(p.getInventory().getItemInOffHand(), "god_mace");
    }

    /** Gibt Mace-Traegern am Boden die Flug-Erlaubnis (= Doppelsprung-Trigger). */
    private void maceFlugCheck() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
            if (traegtGodMace(p)) {
                if (p.isOnGround() && !p.getAllowFlight()) {
                    p.setAllowFlight(true);
                    doppelsprungBereit.add(p.getUniqueId());
                }
            } else if (doppelsprungBereit.remove(p.getUniqueId())) {
                p.setAllowFlight(false);
            }
        }
    }

    /** Doppelt Leertaste = Doppelsprung (Flug-Toggle abfangen). */
    @EventHandler
    public void onDoppelsprung(PlayerToggleFlightEvent event) {
        Player p = event.getPlayer();
        if (!doppelsprungBereit.remove(p.getUniqueId())) return;
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true);
        p.setAllowFlight(false); // bis zur naechsten Landung
        p.setVelocity(p.getLocation().getDirection().clone().multiply(0.8).setY(0.9));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.4f);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.3, 0.1, 0.3, 0.05);
    }

    /** Mit God Mace in der Hand gibt es keinen Fallschaden. */
    @EventHandler
    public void onMaceFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (traegtGodMace(p)) event.setCancelled(true);
    }

    // ────────────────────────── Lunge ohne Hunger ──────────────────────────

    /**
     * Die Lunge-Attacke kostet keinen Hunger mehr: Hunger, Saettigung und
     * Erschoepfung werden vor dem Ansturm gemerkt und einen Tick spaeter
     * wiederhergestellt.
     */
    @EventHandler
    public void onLunge(EntityLungeEvent event) {
        if (!(event.getEntity() instanceof Player p)) return;
        int food = p.getFoodLevel();
        float saturation = p.getSaturation();
        float exhaustion = p.getExhaustion();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            p.setFoodLevel(food);
            p.setSaturation(saturation);
            p.setExhaustion(exhaustion);
        });
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
