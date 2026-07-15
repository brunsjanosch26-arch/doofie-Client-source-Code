package de.doofie.skywars;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DOOFIE SKYWARS-DUELL (GommeHD-Style, max. 2 Spieler).
 *
 * Karte (wird vom Plugin in die Void-Welt gebaut, y=100):
 *   — 2 SPAWN-INSELN (9x9, Gras) bei x=-45 und x=+45, je mit EIGENEM
 *     Haendler-Villager und Start-Kiste.
 *   — pro Spieler 2 NEBEN-INSELN (5x5): EISEN-SPAWNER (alle 20s 2x Eisen)
 *     und DIAMANT-SPAWNER (alle 20s 1 Diamant) — hinbruecken!
 *   — MITTEL-INSEL (19x19, Steinziegel-Optik) ca. 30 Bloecke von den
 *     Spawns entfernt, mit Loot-Kisten — hier wird gekaempft.
 *
 * Ablauf: 2 Spieler joinen -> jeder auf SEINE Insel -> 3s-Freeze ->
 * farmen, beim eigenen Haendler einkaufen (Eisen/Diamant als Waehrung,
 * Preise nach Bedwars/Gomme-Vorbild) -> Fight! Tod, Void, /lobby oder
 * Disconnect = Gegner gewinnt. Danach beide zurueck zur Lobby und die
 * Karte wird komplett zurueckgesetzt.
 */
public class SkywarsPlugin extends JavaPlugin implements Listener {

    private enum Phase { WARTEN, COUNTDOWN, KAMPF, ENDE }

    private static final int Y = 100;
    private static final int SPAWN_X = 45;   // Spawn-Inseln bei +/- SPAWN_X
    private static final int NEBEN_Z = 22;   // Neben-Inseln bei z = +/- NEBEN_Z

    private Phase phase = Phase.WARTEN;
    /** Vom Spieler gesetzte Bloecke (nur die duerfen abgebaut werden). */
    private final Set<Location> gesetzt = new HashSet<>();
    private final List<Location> lootKisten = new ArrayList<>();
    private final List<Location> spawner = new ArrayList<>(); // Eisen+Diamant-Droppunkte

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        new LobbyCommand(this).register();

        World w = welt();
        w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
        try {
            w.setTime(6000);
        } catch (IllegalArgumentException ignoriert) { }

        baueKarte();
        // Spawner-Takt: alle 20 Sekunden
        Bukkit.getScheduler().runTaskTimer(this, this::spawnerTick, 100L, 20L * 20);
        getLogger().info("SkyWars-Duell aktiv — Karte steht, Haendler warten.");
    }

    private World welt() {
        return Bukkit.getWorlds().get(0);
    }

    private Location spawnOrt(boolean links) {
        int x = links ? -SPAWN_X : SPAWN_X;
        Location l = new Location(welt(), x + 0.5, Y + 1, 0.5);
        l.setYaw(links ? -90 : 90);
        return l;
    }

    // ────────────────────────── Karte bauen ──────────────────────────

    private void baueKarte() {
        World w = welt();
        lootKisten.clear();
        spawner.clear();

        for (int seite : new int[]{-1, 1}) {
            int cx = seite * SPAWN_X;
            // Spawn-Insel 9x9 (Gras auf Erde)
            plattform(w, cx, 0, 4, Material.GRASS_BLOCK, Material.DIRT);
            // Start-Kiste
            Block kiste = w.getBlockAt(cx, Y + 1, -3);
            kiste.setType(Material.CHEST);
            if (kiste.getState() instanceof Chest c) {
                c.getInventory().clear();
                c.getInventory().addItem(
                    new ItemStack(Material.SANDSTONE, 64),
                    new ItemStack(Material.STONE_SWORD),
                    new ItemStack(Material.COOKED_BEEF, 8));
            }
            // Haendler-Platz (Villager kommt beim Match-Start)
            // Neben-Inseln: Eisen (z=-NEBEN_Z) und Diamant (z=+NEBEN_Z)
            plattform(w, cx, -NEBEN_Z, 2, Material.STONE, Material.COBBLESTONE);
            w.getBlockAt(cx, Y + 1, -NEBEN_Z).setType(Material.IRON_BLOCK);
            spawner.add(new Location(w, cx + 0.5, Y + 2.2, -NEBEN_Z + 0.5));
            plattform(w, cx, NEBEN_Z, 2, Material.STONE, Material.COBBLESTONE);
            w.getBlockAt(cx, Y + 1, NEBEN_Z).setType(Material.DIAMOND_BLOCK);
            spawner.add(new Location(w, cx + 0.5, Y + 2.2, NEBEN_Z + 0.5));
        }

        // Mittel-Insel 19x19 — gleiche Optik, andere Palette (Steinziegel-Mix)
        World w2 = w;
        var zufall = ThreadLocalRandom.current();
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                if (dx * dx + dz * dz > 9 * 9 + 4) continue;
                Material top = switch (zufall.nextInt(4)) {
                    case 0 -> Material.MOSSY_STONE_BRICKS;
                    case 1 -> Material.CRACKED_STONE_BRICKS;
                    default -> Material.STONE_BRICKS;
                };
                w2.getBlockAt(dx, Y, dz).setType(top);
                w2.getBlockAt(dx, Y - 1, dz).setType(Material.POLISHED_ANDESITE);
            }
        }
        // Loot-Kisten auf der Mitte
        for (int[] pos : new int[][]{{-5, 0}, {5, 0}, {0, 5}}) {
            Block k = w2.getBlockAt(pos[0], Y + 1, pos[1]);
            k.setType(Material.CHEST);
            lootKisten.add(k.getLocation());
        }
        fuelleLoot();
        w.setSpawnLocation(spawnOrt(true));
    }

    private void plattform(World w, int cx, int cz, int radius, Material oben, Material unten) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) == radius && Math.abs(dz) == radius && radius > 2) continue;
                w.getBlockAt(cx + dx, Y, cz + dz).setType(oben);
                w.getBlockAt(cx + dx, Y - 1, cz + dz).setType(unten);
            }
        }
    }

    private void fuelleLoot() {
        List<ItemStack> pool = List.of(
            new ItemStack(Material.IRON_INGOT, 6), new ItemStack(Material.DIAMOND, 2),
            new ItemStack(Material.GOLDEN_APPLE, 1), new ItemStack(Material.ENDER_PEARL, 1),
            new ItemStack(Material.ARROW, 12), new ItemStack(Material.SANDSTONE, 32),
            new ItemStack(Material.COOKED_BEEF, 6), new ItemStack(Material.STONE_AXE));
        var zufall = ThreadLocalRandom.current();
        for (Location ort : lootKisten) {
            if (!(ort.getBlock().getState() instanceof Chest kiste)) continue;
            var inv = kiste.getBlockInventory();
            inv.clear();
            for (int i = 0; i < 4 + zufall.nextInt(3); i++) {
                inv.setItem(zufall.nextInt(inv.getSize()), pool.get(zufall.nextInt(pool.size())).clone());
            }
        }
    }

    // ────────────────────────── Haendler (Gomme/Bedwars-Preise) ──────────────────────────

    private void spawneHaendler() {
        for (int seite : new int[]{-1, 1}) {
            Location ort = new Location(welt(), seite * SPAWN_X + 0.5, Y + 1, 3.5);
            Villager v = (Villager) welt().spawnEntity(ort, EntityType.VILLAGER);
            v.setProfession(Villager.Profession.WEAPONSMITH);
            v.setVillagerLevel(5);
            v.setAI(false);
            v.setSilent(true);
            v.setInvulnerable(true);
            v.setPersistent(true);
            v.customName(Component.text("Haendler", NamedTextColor.GOLD, TextDecoration.BOLD));
            v.setCustomNameVisible(true);
            v.setRotation(seite < 0 ? -90 : 90, 0);

            List<MerchantRecipe> handel = new ArrayList<>();
            // Eisen-Waehrung
            handel.add(handel(Material.IRON_INGOT, 2, new ItemStack(Material.SANDSTONE, 16)));
            handel.add(handel(Material.IRON_INGOT, 4, new ItemStack(Material.COOKED_BEEF, 6)));
            handel.add(handel(Material.IRON_INGOT, 6, new ItemStack(Material.STONE_SWORD)));
            handel.add(handel(Material.IRON_INGOT, 6, new ItemStack(Material.ARROW, 12)));
            handel.add(handel(Material.IRON_INGOT, 10, new ItemStack(Material.BOW)));
            handel.add(handel(Material.IRON_INGOT, 8, new ItemStack(Material.SHIELD)));
            handel.add(handel(Material.IRON_INGOT, 10, new ItemStack(Material.IRON_HELMET)));
            handel.add(handel(Material.IRON_INGOT, 14, new ItemStack(Material.IRON_CHESTPLATE)));
            handel.add(handel(Material.IRON_INGOT, 12, new ItemStack(Material.IRON_LEGGINGS)));
            handel.add(handel(Material.IRON_INGOT, 8, new ItemStack(Material.IRON_BOOTS)));
            // Diamant-Waehrung
            handel.add(handel(Material.DIAMOND, 6, new ItemStack(Material.DIAMOND_SWORD)));
            handel.add(handel(Material.DIAMOND, 3, new ItemStack(Material.GOLDEN_APPLE)));
            handel.add(handel(Material.DIAMOND, 5, new ItemStack(Material.DIAMOND_HELMET)));
            handel.add(handel(Material.DIAMOND, 8, new ItemStack(Material.DIAMOND_CHESTPLATE)));
            handel.add(handel(Material.DIAMOND, 7, new ItemStack(Material.DIAMOND_LEGGINGS)));
            handel.add(handel(Material.DIAMOND, 5, new ItemStack(Material.DIAMOND_BOOTS)));
            ItemStack powerBogen = new ItemStack(Material.BOW);
            ItemMeta pm = powerBogen.getItemMeta();
            pm.addEnchant(Enchantment.POWER, 2, true);
            powerBogen.setItemMeta(pm);
            handel.add(handel(Material.DIAMOND, 10, powerBogen));
            v.setRecipes(handel);
        }
    }

    private MerchantRecipe handel(Material waehrung, int preis, ItemStack ware) {
        MerchantRecipe r = new MerchantRecipe(ware, 999);
        r.addIngredient(new ItemStack(waehrung, preis));
        return r;
    }

    private void entferneHaendler() {
        for (Entity e : welt().getEntities()) {
            if (e instanceof Villager || e instanceof Item) e.remove();
        }
    }

    // ────────────────────────── Spawner ──────────────────────────

    private void spawnerTick() {
        if (phase != Phase.KAMPF) return;
        World w = welt();
        for (Location ort : spawner) {
            boolean diamant = ort.getBlockZ() > 0;
            ItemStack drop = diamant ? new ItemStack(Material.DIAMOND, 1)
                                     : new ItemStack(Material.IRON_INGOT, 2);
            Item item = w.dropItem(ort, drop);
            item.setVelocity(new org.bukkit.util.Vector(0, 0.1, 0));
            w.playSound(ort, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, diamant ? 1.8f : 1.2f);
        }
    }

    // ────────────────────────── Match-Ablauf ──────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        reset(p);
        boolean erster = Bukkit.getOnlinePlayers().size() <= 1;
        p.teleport(spawnOrt(erster));
        p.sendMessage(Component.text("🏝 SKYWARS-DUELL! Farme Eisen & Diamanten auf deinen Neben-Inseln, "
            + "kauf beim Haendler ein — und dann: Fight auf der Mittel-Insel!", NamedTextColor.AQUA));
        pruefeStart();
    }

    private void pruefeStart() {
        if (phase != Phase.WARTEN) return;
        if (Bukkit.getOnlinePlayers().size() < 2) return;
        phase = Phase.COUNTDOWN;
        spawneHaendler();

        List<Player> spieler = new ArrayList<>(Bukkit.getOnlinePlayers());
        spieler.get(0).teleport(spawnOrt(true));
        spieler.get(1).teleport(spawnOrt(false));
        for (Player p : spieler) reset(p);

        final int[] rest = {3};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (Bukkit.getOnlinePlayers().size() < 2) {
                task.cancel();
                phase = Phase.WARTEN;
                return;
            }
            if (rest[0] > 0) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(
                        Component.text(rest[0], NamedTextColor.AQUA, TextDecoration.BOLD),
                        Component.text("Farmen, kaufen, kaempfen!", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
                }
                rest[0]--;
            } else {
                task.cancel();
                phase = Phase.KAMPF;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(
                        Component.text("LOS!", NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(300))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.6f);
                }
            }
        }, 20L, 20L);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (phase == Phase.COUNTDOWN) {
            if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getZ() != event.getTo().getZ()) {
                Location zurueck = event.getFrom().clone();
                zurueck.setYaw(event.getTo().getYaw());
                zurueck.setPitch(event.getTo().getPitch());
                event.setTo(zurueck);
            }
            return;
        }
        // Void-Sturz
        if (event.getTo().getY() < Y - 25) {
            Player p = event.getPlayer();
            if (phase == Phase.KAMPF) {
                niederlage(p, p.getName() + " ist in die Leere gestuerzt");
            } else {
                p.teleport(spawnOrt(true));
            }
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String cmd = event.getMessage().substring(1).split(" ")[0].toLowerCase(Locale.ROOT);
        if (phase == Phase.KAMPF && List.of("lobby", "hub", "l").contains(cmd)) {
            niederlage(event.getPlayer(), event.getPlayer().getName() + " hat aufgegeben");
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().clear();
        if (phase == Phase.KAMPF) {
            niederlage(event.getEntity(), event.getEntity().getName() + " wurde erledigt");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (phase == Phase.KAMPF || phase == Phase.COUNTDOWN) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(event.getPlayer())) {
                    sieg(p, event.getPlayer().getName() + " hat den Kampf verlassen");
                    return;
                }
            }
        }
        phase = Phase.WARTEN;
        if (Bukkit.getOnlinePlayers().size() <= 1) resetKarte();
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (phase != Phase.KAMPF && event.getEntity() instanceof Player) event.setCancelled(true);
    }

    /** Nur selbst gesetzte Bloecke duerfen weg — die Inseln bleiben heil. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!gesetzt.remove(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (phase != Phase.KAMPF) {
            event.setCancelled(true);
            return;
        }
        gesetzt.add(event.getBlock().getLocation());
    }

    // ────────────────────────── Sieg & Reset ──────────────────────────

    private void niederlage(Player verlierer, String grund) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(verlierer)) {
                sieg(p, grund);
                return;
            }
        }
        phase = Phase.WARTEN;
    }

    private void sieg(Player gewinner, String grund) {
        if (phase == Phase.ENDE) return;
        phase = Phase.ENDE;
        Bukkit.broadcast(Component.text("🏆 " + gewinner.getName() + " GEWINNT das SkyWars-Duell! (" + grund + ")",
            NamedTextColor.GOLD, TextDecoration.BOLD));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                Component.text(p.equals(gewinner) ? "SIEG!" : "NIEDERLAGE",
                    p.equals(gewinner) ? NamedTextColor.GOLD : NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(grund, NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ofSeconds(1))));
        }
        gewinner.playSound(gewinner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                reset(p);
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF("lobby");
                p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
            }
            resetKarte();
            phase = Phase.WARTEN;
        }, 100L);
    }

    /** Karte frisch machen: gesetzte Bloecke weg, Inseln neu, Loot neu, Entities weg. */
    private void resetKarte() {
        for (Location ort : gesetzt) {
            ort.getBlock().setType(Material.AIR);
        }
        gesetzt.clear();
        entferneHaendler();
        baueKarte();
    }

    private void reset(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.setArrowsInBody(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
    }
}
