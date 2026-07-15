package de.doofie.pvp;

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
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * DOOFIE 1vs1 (GommeHD-Style) — Void-Welt mit .litematic-Arena.
 *
 * — Die Arena (plugins/DoofiePvp/arena.dschem) wird beim ersten Start
 *   in die leere Welt gesetzt; die Spieler spawnen an den beiden
 *   gegenueberliegenden Enden, Blick aufeinander.
 * — 2 Spieler online -> Kit sofort ins Inventar (essen/sortieren erlaubt),
 *   3s-Countdown mit Bewegungs-Freeze, dann KAMPF.
 * — Arena verlassen unmoeglich (Rueckteleport). Void-Sturz, /lobby
 *   oder Disconnect im Kampf = der Gegner gewinnt.
 * — Nach dem Duell gehen beide automatisch zurueck in die Lobby.
 *
 * Kits (nach GommeHD-Vorbild, config.yml 'kit: sword|uhc'):
 *   SWORD: volle Diamant-Ruestung, Diamantschwert (Schaerfe I), Steaks.
 *   UHC:   volle Eisen-Ruestung, Eisenschwert (Schaerfe I), Bogen + 32
 *          Pfeile, Golden Head, Wasser- & Lava-Eimer, 32 Cobble, 8 Steaks,
 *          Angel — natuerliche Regeneration AUS.
 */
public class PvpPlugin extends JavaPlugin implements Listener {

    private enum Phase { WARTEN, COUNTDOWN, KAMPF, ENDE }

    private Phase phase = Phase.WARTEN;
    private SchematicLader arena;
    private int arenaMinX, arenaMinY, arenaMinZ; // Platzierungs-Ursprung
    private Location spawnA, spawnB;
    private final List<Location> frozen = new ArrayList<>(); // [0]=A, [1]=B

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
        w.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, !kit().equals("uhc"));

        try {
            arena = new SchematicLader(new File(getDataFolder(), "arena.dschem"));
            arenaMinX = -arena.breite / 2;
            arenaMinY = 100;
            arenaMinZ = -arena.laenge / 2;
            platzelfalls();
            fuelleBoden();
            berechneSpawns();
        } catch (Exception ex) {
            getLogger().severe("Arena konnte nicht geladen werden: " + ex.getMessage());
        }
        getLogger().info("1vs1 aktiv — Kit: " + kit().toUpperCase() + ", Arena "
            + (arena != null ? arena.breite + "x" + arena.hoehe + "x" + arena.laenge : "FEHLT"));
    }

    private String kit() {
        return getConfig().getString("kit", "sword").toLowerCase(Locale.ROOT);
    }

    private World welt() {
        return Bukkit.getWorlds().get(0);
    }

    // ────────────────────────── Arena ──────────────────────────

    /** Setzt die Arena einmalig in die Void-Welt (Marker-Datei). */
    private void platzelfalls() {
        File marker = new File(getDataFolder(), "arena-gebaut.marker");
        if (marker.exists()) return;
        World w = welt();
        int gesetzt = 0;
        for (int y = 0; y < arena.hoehe; y++) {
            for (int z = 0; z < arena.laenge; z++) {
                for (int x = 0; x < arena.breite; x++) {
                    BlockData bd = arena.blockAn(x, y, z);
                    if (bd == null) continue;
                    w.getBlockAt(arenaMinX + x, arenaMinY + y, arenaMinZ + z).setBlockData(bd, false);
                    gesetzt++;
                }
            }
        }
        try {
            marker.createNewFile();
        } catch (Exception ignored) { }
        getLogger().info("Arena platziert: " + gesetzt + " Bloecke.");
    }

    /**
     * Fehlt der Arena-INNENBODEN (z.B. Kolosseum, das auf Erdboden gebaut
     * wurde), wird er zeilenweise zwischen den Aussenwaenden mit Sand gefuellt.
     */
    private void fuelleBoden() {
        World w = welt();
        int y = arenaMinY;
        int mx = arenaMinX + arena.breite / 2, mz = arenaMinZ + arena.laenge / 2;
        if (!w.getBlockAt(mx, y, mz).getType().isAir()) return; // Boden vorhanden
        int gefuellt = 0;
        for (int z = 0; z < arena.laenge; z++) {
            // erste/letzte feste Zelle der Zeile finden — dazwischen ist "innen"
            int erster = -1, letzter = -1;
            for (int x = 0; x < arena.breite; x++) {
                if (!w.getBlockAt(arenaMinX + x, y, arenaMinZ + z).getType().isAir()) {
                    if (erster < 0) erster = x;
                    letzter = x;
                }
            }
            for (int x = erster + 1; x < letzter; x++) {
                Block b = w.getBlockAt(arenaMinX + x, y, arenaMinZ + z);
                if (b.getType().isAir()) {
                    b.setType(Material.SAND);
                    gefuellt++;
                }
            }
        }
        if (gefuellt > 0) getLogger().info("Arena-Boden aufgefuellt: " + gefuellt + " Bloecke Sand.");
    }

    /**
     * Spawns AUF DEM ARENABODEN (nicht auf der Tribuene): von der Mitte aus
     * auf Bodenhoehe nach aussen laufen, bis die Wand kommt — 4 Bloecke davor.
     */
    private void berechneSpawns() {
        World w = welt();
        int mx = arenaMinX + arena.breite / 2, mz = arenaMinZ + arena.laenge / 2;
        int bodenY = arenaMinY + 1; // stehen AUF der y0-Schicht

        int distA = wandAbstand(w, mx, mz, -1, bodenY);
        int distB = wandAbstand(w, mx, mz, 1, bodenY);
        spawnA = new Location(w, mx - distA + 0.5, bodenY, mz + 0.5, -90, 0);
        spawnB = new Location(w, mx + distB + 0.5, bodenY, mz + 0.5, 90, 0);
        // Falls unter dem Spawn doch Luft ist: Saeule absenken bis Boden
        for (Location s : List.of(spawnA, spawnB)) {
            while (s.getY() > arenaMinY - 2
                && w.getBlockAt(s.getBlockX(), s.getBlockY() - 1, s.getBlockZ()).getType().isAir()) {
                s.subtract(0, 1, 0);
            }
        }
        getLogger().info("Spawns auf dem Arenaboden: A=" + spawnA.getBlockX() + "/" + spawnA.getBlockY()
            + " B=" + spawnB.getBlockX() + "/" + spawnB.getBlockY());
    }

    /** Wie weit von der Mitte bis zur Wand (Bloecke in Kopf-/Fusshoehe)? */
    private int wandAbstand(World w, int mx, int mz, int richtung, int bodenY) {
        int max = arena.breite / 2 - 1;
        for (int d = 3; d <= max; d++) {
            int x = mx + richtung * d;
            for (int dy = 0; dy <= 4; dy++) {
                if (!w.getBlockAt(x, bodenY + dy, mz).getType().isAir()) {
                    return Math.max(3, d - 4);
                }
            }
        }
        return Math.max(3, max - 4);
    }

    private boolean inArena(Location loc) {
        return loc.getX() >= arenaMinX - 1 && loc.getX() <= arenaMinX + arena.breite + 1
            && loc.getZ() >= arenaMinZ - 1 && loc.getZ() <= arenaMinZ + arena.laenge + 1;
    }

    // ────────────────────────── Ablauf ──────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        reset(p);
        p.teleport(spawnA != null ? spawnA : welt().getSpawnLocation());
        p.sendMessage(Component.text("⚔ 1vs1 (" + kit().toUpperCase()
            + ") — sobald dein Gegner da ist: Kit, 3s Countdown, KAMPF!", NamedTextColor.GOLD));
        pruefeStart();
    }

    private void pruefeStart() {
        if (phase != Phase.WARTEN) return;
        List<Player> spieler = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (spieler.size() < 2) return;
        phase = Phase.COUNTDOWN;

        Player a = spieler.get(0), b = spieler.get(1);
        a.teleport(spawnA);
        b.teleport(spawnB);
        frozen.clear();
        frozen.add(spawnA);
        frozen.add(spawnB);
        for (Player p : List.of(a, b)) {
            reset(p);
            gibKit(p);
            p.sendMessage(Component.text("Kit ist da — du kannst schon essen und sortieren!", NamedTextColor.GREEN));
        }

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
                        Component.text(rest[0], NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.2f);
                }
                rest[0]--;
            } else {
                task.cancel();
                phase = Phase.KAMPF;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(
                        Component.text("KAMPF!", NamedTextColor.RED, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofMillis(300))));
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.5f);
                }
            }
        }, 20L, 20L);
    }

    /** Freeze im Countdown + Arena-Grenzen + Void = Niederlage. */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (phase == Phase.COUNTDOWN) {
            // Position einfrieren, Kopf drehen erlaubt
            if (event.getFrom().getX() != event.getTo().getX()
                || event.getFrom().getZ() != event.getTo().getZ()) {
                Location zurueck = event.getFrom().clone();
                zurueck.setYaw(event.getTo().getYaw());
                zurueck.setPitch(event.getTo().getPitch());
                event.setTo(zurueck);
            }
            return;
        }
        if (arena == null) return;
        // Void-Sturz = Niederlage
        if (event.getTo().getY() < arenaMinY - 5) {
            if (phase == Phase.KAMPF) {
                niederlage(p, p.getName() + " ist in die Leere gestuerzt");
            } else {
                p.teleport(spawnA);
            }
            return;
        }
        // Arena nicht verlassen
        if (!inArena(event.getTo())) {
            event.setCancelled(true);
        }
    }

    /** /lobby (oder /hub) im Kampf = Aufgabe. */
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
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (phase != Phase.KAMPF && event.getEntity() instanceof Player) event.setCancelled(true);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        if (phase != Phase.KAMPF) event.setCancelled(true);
    }

    /** Arena bleibt heil — nur UHC darf Cobble/Wasser/Lava setzen. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!kit().equals("uhc") || phase != Phase.KAMPF) event.setCancelled(true);
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
        Bukkit.broadcast(Component.text("🏆 " + gewinner.getName() + " GEWINNT! (" + grund + ")",
            NamedTextColor.GOLD, TextDecoration.BOLD));
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(
                Component.text(p.equals(gewinner) ? "SIEG!" : "NIEDERLAGE",
                    p.equals(gewinner) ? NamedTextColor.GOLD : NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(grund, NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(4), Duration.ofSeconds(1))));
        }
        gewinner.playSound(gewinner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        // Nach 5s: beide zurueck in die Lobby
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                reset(p);
                ByteArrayDataOutput out = ByteStreams.newDataOutput();
                out.writeUTF("Connect");
                out.writeUTF("lobby");
                p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
            }
            phase = Phase.WARTEN;
        }, 100L);
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

    // ────────────────────────── Kits (GommeHD-Vorbild) ──────────────────────────

    private void gibKit(Player p) {
        var inv = p.getInventory();
        if (kit().equals("uhc")) {
            inv.setHelmet(new ItemStack(Material.IRON_HELMET));
            inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            inv.setBoots(new ItemStack(Material.IRON_BOOTS));
            ItemStack schwert = new ItemStack(Material.IRON_SWORD);
            ItemMeta sm = schwert.getItemMeta();
            sm.addEnchant(Enchantment.SHARPNESS, 1, true);
            schwert.setItemMeta(sm);
            // "Golden Head" — Goldapfel mit Regeneration II wie auf Gomme
            ItemStack goldenHead = new ItemStack(Material.GOLDEN_APPLE);
            ItemMeta gh = goldenHead.getItemMeta();
            gh.displayName(Component.text("Golden Head", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
            goldenHead.setItemMeta(gh);
            inv.setItem(0, schwert);
            inv.setItem(1, new ItemStack(Material.BOW));
            inv.setItem(2, goldenHead);
            inv.setItem(3, new ItemStack(Material.WATER_BUCKET));
            inv.setItem(4, new ItemStack(Material.LAVA_BUCKET));
            inv.setItem(5, new ItemStack(Material.COBBLESTONE, 32));
            inv.setItem(6, new ItemStack(Material.COOKED_BEEF, 8));
            inv.setItem(7, new ItemStack(Material.FISHING_ROD));
            inv.setItem(8, new ItemStack(Material.ARROW, 32));
        } else { // sword
            inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            inv.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
            inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
            ItemStack schwert = new ItemStack(Material.DIAMOND_SWORD);
            ItemMeta sm = schwert.getItemMeta();
            sm.addEnchant(Enchantment.SHARPNESS, 1, true);
            sm.setUnbreakable(true);
            schwert.setItemMeta(sm);
            inv.setItem(0, schwert);
            inv.setItem(1, new ItemStack(Material.COOKED_BEEF, 64));
        }
        // Gomme-Feeling: Absorption weg, volle Herzen
        p.removePotionEffect(PotionEffectType.ABSORPTION);
    }

    /** Golden Head: beim Essen Regeneration II fuer 9 Sekunden. */
    @EventHandler
    public void onEat(org.bukkit.event.player.PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.GOLDEN_APPLE) return;
        if (!event.getItem().hasItemMeta() || !event.getItem().getItemMeta().hasDisplayName()) return;
        event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 9 * 20, 1));
    }
}
