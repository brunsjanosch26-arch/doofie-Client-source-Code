package de.doofie.zombie;

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
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Horden-Spawner, Blutmond und die drei Verteidigungs-Items.
 */
public class ApokalypseManager implements Listener, TabExecutor {

    private final ZombiePlugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey spuckerKey;
    /** Positionen aktiver Selbstschuss-Tuerme */
    private final Set<Location> tuerme = new HashSet<>();
    /** Positionen von Stacheldraht-Netzen */
    private final Set<Location> stacheldraht = new HashSet<>();

    public ApokalypseManager(ZombiePlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
        this.spuckerKey = new NamespacedKey(plugin, "spucker");
    }

    public void start() {
        // Horden-Spawner: alle 30s nachts
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawneHorden, 200L, 600L);
        // Tuerme + Stacheldraht: alle 2s
        Bukkit.getScheduler().runTaskTimer(plugin, this::verteidigung, 40L, 40L);
        // Blutmond-Ansage bei Nachtanbruch
        Bukkit.getScheduler().runTaskTimer(plugin, this::blutmondCheck, 100L, 100L);

        // Rezepte
        var draht = new org.bukkit.inventory.ShapedRecipe(new NamespacedKey(plugin, "stacheldraht"), stacheldraht());
        draht.shape("I I", " S ", "I I");
        draht.setIngredient('I', Material.IRON_INGOT);
        draht.setIngredient('S', Material.STRING);
        Bukkit.addRecipe(draht);

        var turm = new org.bukkit.inventory.ShapedRecipe(new NamespacedKey(plugin, "selbstschuss_turm"), turm());
        turm.shape("IPI", "PDP", "IRI");
        turm.setIngredient('I', Material.IRON_BLOCK);
        turm.setIngredient('P', Material.ARROW);
        turm.setIngredient('D', Material.DISPENSER);
        turm.setIngredient('R', Material.REDSTONE_BLOCK);
        Bukkit.addRecipe(turm);

        var spritze = new org.bukkit.inventory.ShapedRecipe(new NamespacedKey(plugin, "adrenalin_spritze"), spritze());
        spritze.shape(" I ", " G ", " B ");
        spritze.setIngredient('I', Material.IRON_NUGGET);
        spritze.setIngredient('G', Material.GLASS_BOTTLE);
        spritze.setIngredient('B', Material.BLAZE_POWDER);
        Bukkit.addRecipe(spritze);
    }

    // ────────────────────────── Nacht-Logik ──────────────────────────

    private boolean istNacht(World w) {
        return w.getTime() >= 13000 && w.getTime() <= 23000;
    }

    /** Jede 7. Nacht (Tag % 7 == 6) ist Blutmond. */
    private boolean istBlutmond(World w) {
        return (w.getFullTime() / 24000) % 7 == 6;
    }

    private long letzteAnsage = -1;

    private void blutmondCheck() {
        World w = Bukkit.getWorlds().get(0);
        long tag = w.getFullTime() / 24000;
        if (istNacht(w) && istBlutmond(w) && tag != letzteAnsage) {
            letzteAnsage = tag;
            Bukkit.broadcast(Component.text("🩸 BLUTMOND! Die Horden sind doppelt so gross und staerker — versteckt euch!",
                NamedTextColor.DARK_RED, TextDecoration.BOLD));
            Bukkit.getOnlinePlayers().forEach(p ->
                p.playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 2f, 0.5f));
        }
    }

    private void spawneHorden() {
        World w = Bukkit.getWorlds().get(0);
        if (!istNacht(w)) return;
        boolean blutmond = istBlutmond(w);
        int proSpieler = blutmond ? 6 : 3;

        for (Player p : w.getPlayers()) {
            for (int i = 0; i < proSpieler; i++) {
                Location spawn = p.getLocation().clone().add(
                    ThreadLocalRandom.current().nextInt(-24, 25), 0,
                    ThreadLocalRandom.current().nextInt(-24, 25));
                spawn.setY(w.getHighestBlockYAt(spawn) + 1);
                if (Math.abs(spawn.getY() - p.getLocation().getY()) > 24) continue;

                Zombie z = (Zombie) w.spawnEntity(spawn, org.bukkit.entity.EntityType.ZOMBIE);
                int typ = ThreadLocalRandom.current().nextInt(3);
                switch (typ) {
                    case 0 -> { // SPRINTER
                        z.customName(Component.text("Sprinter", NamedTextColor.YELLOW));
                        z.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 1));
                    }
                    case 1 -> { // TANK
                        z.customName(Component.text("Tank", NamedTextColor.DARK_GRAY));
                        z.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40.0);
                        z.setHealth(40.0);
                        z.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, Integer.MAX_VALUE, 0));
                    }
                    default -> { // SPUCKER
                        z.customName(Component.text("Spucker", NamedTextColor.GREEN));
                        z.getPersistentDataContainer().set(spuckerKey, PersistentDataType.BYTE, (byte) 1);
                    }
                }
                if (blutmond) {
                    z.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0));
                }
                z.setTarget(p);
            }
        }
    }

    /** Spucker vergiften beim Treffer. */
    @EventHandler
    public void onSpuckerHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Zombie z)) return;
        if (!z.getPersistentDataContainer().has(spuckerKey, PersistentDataType.BYTE)) return;
        if (!(event.getEntity() instanceof Player p)) return;
        p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
        p.sendMessage(Component.text("Igitt — der Spucker hat dich vergiftet!", NamedTextColor.GREEN));
    }

    // ────────────────────────── Verteidigung ──────────────────────────

    private void verteidigung() {
        // Selbstschuss-Tuerme feuern auf das naechste Monster
        for (Location turm : new ArrayList<>(tuerme)) {
            if (turm.getBlock().getType() != Material.DISPENSER) {
                tuerme.remove(turm);
                continue;
            }
            Location mitte = turm.clone().add(0.5, 1.0, 0.5);
            turm.getWorld().getNearbyEntitiesByType(Monster.class, mitte, 12).stream()
                .min(Comparator.comparingDouble(m -> m.getLocation().distanceSquared(mitte)))
                .ifPresent(ziel -> {
                    Vector richtung = ziel.getEyeLocation().toVector().subtract(mitte.toVector()).normalize();
                    Arrow pfeil = turm.getWorld().spawnArrow(mitte, richtung, 2.0f, 1.0f);
                    pfeil.setDamage(6.0);
                    turm.getWorld().playSound(mitte, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.2f);
                });
        }
        // Stacheldraht verletzt Monster im Netz
        for (Location draht : new ArrayList<>(stacheldraht)) {
            if (draht.getBlock().getType() != Material.COBWEB) {
                stacheldraht.remove(draht);
                continue;
            }
            Location mitte = draht.clone().add(0.5, 0.5, 0.5);
            for (Monster m : draht.getWorld().getNearbyEntitiesByType(Monster.class, mitte, 1.0)) {
                m.damage(2.0);
            }
        }
    }

    // ────────────────────────── Items ──────────────────────────

    private ItemStack tagged(Material mat, String id, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("zombie", id));
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

    public ItemStack stacheldraht() {
        return tagged(Material.COBWEB, "stacheldraht", "Stacheldraht", NamedTextColor.GRAY,
            List.of("Rechtsklick auf einen Block:",
                "platziert ein Netz, das Monster",
                "festhaelt UND verletzt."));
    }

    public ItemStack turm() {
        return tagged(Material.DISPENSER, "selbstschuss_turm", "Selbstschuss-Turm", NamedTextColor.RED,
            List.of("Rechtsklick auf einen platzierten WERFER:",
                "er feuert alle 2s Pfeile auf Monster",
                "im 12er-Umkreis. Automatisch!"));
    }

    public ItemStack spritze() {
        ItemStack item = tagged(Material.EXPERIENCE_BOTTLE, "adrenalin_spritze", "Adrenalin-Spritze", NamedTextColor.LIGHT_PURPLE,
            List.of("Rechtsklick: 30s Speed II + Staerke II —",
                "danach 30s grosser Hunger. Einweg."));
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack byId(String id) {
        return switch (id) {
            case "stacheldraht" -> stacheldraht();
            case "selbstschuss_turm" -> turm();
            case "adrenalin_spritze" -> spritze();
            default -> null;
        };
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String id = idOf(event.getItem());
        if (id == null) return;
        Player p = event.getPlayer();

        if (id.equals("stacheldraht") && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block ueber = event.getClickedBlock().getRelative(event.getBlockFace());
            if (!ueber.getType().isAir()) return;
            ueber.setType(Material.COBWEB);
            stacheldraht.add(ueber.getLocation());
            event.getItem().subtract();
            p.playSound(ueber.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1f);
        }

        if (id.equals("selbstschuss_turm") && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block ziel = event.getClickedBlock();
            if (ziel.getType() != Material.DISPENSER) {
                p.sendMessage(Component.text("Rechtsklick auf einen platzierten WERFER!", NamedTextColor.RED));
                return;
            }
            if (!tuerme.add(ziel.getLocation())) return;
            event.getItem().subtract();
            p.playSound(ziel.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            p.sendMessage(Component.text("Turm aktiviert — er verteidigt jetzt selbststaendig!", NamedTextColor.RED));
        }

        if (id.equals("adrenalin_spritze")
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            event.getItem().subtract();
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 1.6f);
            p.sendMessage(Component.text("ADRENALIN! 30 Sekunden Vollgas...", NamedTextColor.LIGHT_PURPLE));
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 600, 2));
                p.sendMessage(Component.text("...und jetzt der Kater. Iss was!", NamedTextColor.GRAY));
            }, 600L);
        }
    }

    /** Turm-Abbau raeumt die Registrierung auf. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        tuerme.remove(event.getBlock().getLocation());
        stacheldraht.remove(event.getBlock().getLocation());
    }

    // ────────────────────────── /apokalypse ──────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status" -> {
                World w = Bukkit.getWorlds().get(0);
                long tag = w.getFullTime() / 24000;
                long bisBlutmond = (6 - (tag % 7) + 7) % 7;
                sender.sendMessage(Component.text("🧟 Tag " + tag + " — "
                    + (istNacht(w) ? (istBlutmond(w) ? "BLUTMOND-NACHT!" : "Nacht — Horden aktiv!")
                        : "Tag — noch sicher.")
                    + " Naechster Blutmond in " + bisBlutmond + " Tagen.",
                    istBlutmond(w) && istNacht(w) ? NamedTextColor.DARK_RED : NamedTextColor.GREEN));
            }
            case "item" -> {
                if (!sender.hasPermission("doofie.zombie.admin") || !(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Nur fuer Admins.", NamedTextColor.RED));
                    return true;
                }
                ItemStack item = args.length > 1 ? byId(args[1].toLowerCase(Locale.ROOT)) : null;
                if (item == null) {
                    sender.sendMessage(Component.text(
                        "Nutzung: /apokalypse item <stacheldraht|selbstschuss_turm|adrenalin_spritze>",
                        NamedTextColor.RED));
                    return true;
                }
                p.getInventory().addItem(item);
            }
            default -> sender.sendMessage(Component.text("Nutzung: /apokalypse [status|item]", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) return List.of("status", "item").stream()
            .filter(s -> s.startsWith(prefix)).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("item"))
            return List.of("stacheldraht", "selbstschuss_turm", "adrenalin_spritze").stream()
                .filter(s -> s.startsWith(prefix)).toList();
        return List.of();
    }
}
