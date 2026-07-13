package de.doofie.chaos;

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
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Der Event-Wuerfel: alle 15 Minuten ein Zufalls-Event — plus die
 * drei Custom-Items (Chaos-Wuerfel, Stabilisator, Gluecks-Trank).
 */
public class ChaosManager implements Listener, TabExecutor {

    private static final long INTERVALL_TICKS = 20L * 60 * 15; // 15 Minuten

    /** Ein Chaos-Event: Name, negativ?, Aktion pro Spieler oder global. */
    private record ChaosEvent(String name, boolean negativ, Runnable global) {}

    private final ChaosPlugin plugin;
    private final NamespacedKey itemKey;
    private final List<ChaosEvent> events = new ArrayList<>();
    /** Wuerfel-Cooldowns */
    private final Map<UUID, Long> wuerfelCooldown = new HashMap<>();
    /** Spieler mit aktivem Gluecks-Trank */
    private final Set<UUID> glueck = new HashSet<>();
    private long naechstesEvent;

    public ChaosManager(ChaosPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
        baueEvents();
    }

    public void start() {
        naechstesEvent = System.currentTimeMillis() + INTERVALL_TICKS * 50;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            wuerfleEvent();
            naechstesEvent = System.currentTimeMillis() + INTERVALL_TICKS * 50;
        }, INTERVALL_TICKS, INTERVALL_TICKS);

        // Rezept: Chaos-Wuerfel
        var wuerfel = new org.bukkit.inventory.ShapedRecipe(new NamespacedKey(plugin, "chaos_wuerfel"), chaosWuerfel());
        wuerfel.shape("EAE", "ADA", "EAE");
        wuerfel.setIngredient('E', Material.ENDER_PEARL);
        wuerfel.setIngredient('A', Material.AMETHYST_SHARD);
        wuerfel.setIngredient('D', Material.DIAMOND_BLOCK);
        Bukkit.addRecipe(wuerfel);
    }

    // ────────────────────────── Events ──────────────────────────

    private List<Player> betroffene(boolean negativ) {
        List<Player> spieler = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (negativ) {
                // Stabilisator im Inventar? Einmal geschuetzt.
                ItemStack stab = findeItem(p, "stabilisator");
                if (stab != null) {
                    stab.subtract();
                    p.sendMessage(Component.text("Dein Stabilisator hat dich beschuetzt (und ist zerbroeselt).",
                        NamedTextColor.AQUA));
                    continue;
                }
                if (glueck.remove(p.getUniqueId())) {
                    p.getInventory().addItem(new ItemStack(Material.DIAMOND, 3));
                    p.sendMessage(Component.text("Dein Gluecks-Trank wirkt — statt Chaos gibts Diamanten!",
                        NamedTextColor.LIGHT_PURPLE));
                    continue;
                }
            }
            spieler.add(p);
        }
        return spieler;
    }

    private void baueEvents() {
        events.add(new ChaosEvent("🌧 ITEM-REGEN — der Himmel schuettet Beute!", false, () -> {
            List<Material> loot = List.of(Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND,
                Material.BREAD, Material.EMERALD, Material.ARROW, Material.COAL, Material.OAK_LOG);
            for (Player p : betroffene(false)) {
                for (int i = 0; i < 10; i++) {
                    Location drop = p.getLocation().clone().add(
                        ThreadLocalRandom.current().nextInt(-8, 9), 6,
                        ThreadLocalRandom.current().nextInt(-8, 9));
                    p.getWorld().dropItemNaturally(drop,
                        new ItemStack(loot.get(ThreadLocalRandom.current().nextInt(loot.size()))));
                }
            }
        }));
        events.add(new ChaosEvent("🔄 INVENTAR-TAUSCH — zwei Zufallsspieler tauschen ALLES!", true, () -> {
            List<Player> kandidaten = betroffene(true);
            if (kandidaten.size() < 2) return;
            Player a = kandidaten.get(ThreadLocalRandom.current().nextInt(kandidaten.size()));
            Player b;
            do {
                b = kandidaten.get(ThreadLocalRandom.current().nextInt(kandidaten.size()));
            } while (b.equals(a));
            ItemStack[] inhaltA = a.getInventory().getContents();
            a.getInventory().setContents(b.getInventory().getContents());
            b.getInventory().setContents(inhaltA);
            Bukkit.broadcast(Component.text(a.getName() + " und " + b.getName()
                + " haben jetzt das Inventar des jeweils anderen!", NamedTextColor.LIGHT_PURPLE));
        }));
        events.add(new ChaosEvent("🪶 LOW GRAVITY — 2 Minuten Mondspruenge!", false, () -> {
            for (Player p : betroffene(false)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 2400, 3));
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 2400, 0));
            }
        }));
        events.add(new ChaosEvent("💣 TNT-HAGEL — in Deckung!", true, () -> {
            for (Player p : betroffene(true)) {
                for (int i = 0; i < 5; i++) {
                    Location drop = p.getLocation().clone().add(
                        ThreadLocalRandom.current().nextInt(-10, 11), 12,
                        ThreadLocalRandom.current().nextInt(-10, 11));
                    TNTPrimed tnt = (TNTPrimed) p.getWorld().spawnEntity(drop, EntityType.TNT);
                    tnt.setFuseTicks(60 + ThreadLocalRandom.current().nextInt(40));
                }
            }
        }));
        events.add(new ChaosEvent("🐷 MOB-PARTY — die Tiere sind los!", false, () -> {
            List<EntityType> tiere = List.of(EntityType.PIG, EntityType.SHEEP, EntityType.COW,
                EntityType.CHICKEN, EntityType.CAT, EntityType.WOLF);
            for (Player p : betroffene(false)) {
                for (int i = 0; i < 6; i++) {
                    Location spawn = p.getLocation().clone().add(
                        ThreadLocalRandom.current().nextInt(-6, 7), 0,
                        ThreadLocalRandom.current().nextInt(-6, 7));
                    spawn.setY(p.getWorld().getHighestBlockYAt(spawn) + 1);
                    p.getWorld().spawnEntity(spawn, tiere.get(ThreadLocalRandom.current().nextInt(tiere.size())));
                }
            }
        }));
        events.add(new ChaosEvent("✨ XP-REGEN — Erfahrung vom Himmel!", false, () -> {
            for (Player p : betroffene(false)) {
                for (int i = 0; i < 8; i++) {
                    Location drop = p.getLocation().clone().add(
                        ThreadLocalRandom.current().nextInt(-5, 6), 4,
                        ThreadLocalRandom.current().nextInt(-5, 6));
                    ExperienceOrb orb = (ExperienceOrb) p.getWorld().spawnEntity(drop, EntityType.EXPERIENCE_ORB);
                    orb.setExperience(20);
                }
            }
        }));
        events.add(new ChaosEvent("💨 SPEED-RUNDE — 2 Minuten Vollgas fuer alle!", false, () -> {
            for (Player p : betroffene(false)) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 2400, 2));
            }
        }));
    }

    public void wuerfleEvent() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        ChaosEvent event = events.get(ThreadLocalRandom.current().nextInt(events.size()));
        Bukkit.broadcast(Component.text("🎲 CHAOS! ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
            .append(Component.text(event.name(), NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
        Bukkit.getOnlinePlayers().forEach(p ->
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.7f));
        event.global().run();
    }

    // ────────────────────────── Items ──────────────────────────

    private ItemStack tagged(Material mat, String id, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("chaos", id));
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

    private ItemStack findeItem(Player p, String id) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (id.equals(idOf(item))) return item;
        }
        return null;
    }

    public ItemStack chaosWuerfel() {
        ItemStack item = tagged(Material.AMETHYST_SHARD, "chaos_wuerfel", "Chaos-Würfel", NamedTextColor.LIGHT_PURPLE,
            List.of("Rechtsklick: loest SOFORT ein",
                "zufaelliges Chaos-Event aus",
                "(1 Stunde Cooldown)."));
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack stabilisator() {
        return tagged(Material.LODESTONE, "stabilisator", "Stabilisator", NamedTextColor.AQUA,
            List.of("Liegt er im Inventar, ueberspringt",
                "dich das naechste NEGATIVE Event.",
                "Wird dabei verbraucht."));
    }

    public ItemStack gluecksTrank() {
        ItemStack item = tagged(Material.HONEY_BOTTLE, "gluecks_trank", "Glücks-Trank", NamedTextColor.GOLD,
            List.of("Rechtsklick: beim naechsten Event",
                "bist du garantiert auf der",
                "Gewinnerseite. Einweg."));
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack byId(String id) {
        return switch (id) {
            case "chaos_wuerfel" -> chaosWuerfel();
            case "stabilisator" -> stabilisator();
            case "gluecks_trank" -> gluecksTrank();
            default -> null;
        };
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = idOf(event.getItem());
        if (id == null) return;
        Player p = event.getPlayer();

        if (id.equals("chaos_wuerfel")) {
            event.setCancelled(true);
            long jetzt = System.currentTimeMillis();
            Long cd = wuerfelCooldown.get(p.getUniqueId());
            if (cd != null && cd > jetzt) {
                p.sendMessage(Component.text("Der Wuerfel ruht noch " + ((cd - jetzt) / 60_000 + 1)
                    + " Minuten.", NamedTextColor.LIGHT_PURPLE));
                return;
            }
            wuerfelCooldown.put(p.getUniqueId(), jetzt + 3_600_000);
            Bukkit.broadcast(Component.text("🎲 " + p.getName() + " hat den Chaos-Wuerfel geworfen!",
                NamedTextColor.LIGHT_PURPLE));
            wuerfleEvent();
        }

        if (id.equals("gluecks_trank")) {
            event.setCancelled(true);
            event.getItem().subtract();
            glueck.add(p.getUniqueId());
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
            p.sendMessage(Component.text("Du fuehlst dich... vom Glueck gekuesst. Naechstes Event: Gewinnerseite!",
                NamedTextColor.GOLD));
        }
    }

    // ────────────────────────── /chaos ──────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "status";
        switch (sub) {
            case "status" -> {
                long rest = Math.max(0, naechstesEvent - System.currentTimeMillis());
                sender.sendMessage(Component.text("🎲 Naechstes Chaos-Event in "
                    + (rest / 60_000) + " Min " + (rest / 1000 % 60) + " Sek.", NamedTextColor.LIGHT_PURPLE));
            }
            case "jetzt" -> {
                if (!sender.hasPermission("doofie.chaos.admin")) {
                    sender.sendMessage(Component.text("Nur fuer Admins.", NamedTextColor.RED));
                    return true;
                }
                wuerfleEvent();
            }
            case "item" -> {
                if (!sender.hasPermission("doofie.chaos.admin") || !(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Nur fuer Admins.", NamedTextColor.RED));
                    return true;
                }
                ItemStack item = args.length > 1 ? byId(args[1].toLowerCase(Locale.ROOT)) : null;
                if (item == null) {
                    sender.sendMessage(Component.text(
                        "Nutzung: /chaos item <chaos_wuerfel|stabilisator|gluecks_trank>", NamedTextColor.RED));
                    return true;
                }
                p.getInventory().addItem(item);
            }
            default -> sender.sendMessage(Component.text("Nutzung: /chaos [status|jetzt|item]", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) return List.of("status", "jetzt", "item").stream()
            .filter(s -> s.startsWith(prefix)).toList();
        if (args.length == 2 && args[0].equalsIgnoreCase("item"))
            return List.of("chaos_wuerfel", "stabilisator", "gluecks_trank").stream()
                .filter(s -> s.startsWith(prefix)).toList();
        return List.of();
    }
}
