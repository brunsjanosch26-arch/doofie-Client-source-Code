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
import org.bukkit.block.data.BlockData;
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

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DOOFIE SKYWARS-DUELL (max. 2 Spieler) — Schematik-Karte in der Void-Welt:
 *
 *   — 2 SPAWN-INSELN (island3e, 107x174x235) bei x=-118 und x=+118,
 *     GEGENUEBER voneinander — jeder Spieler spawnt auf SEINER Insel
 *     mit EIGENEM Haendler-Villager und Start-Kiste.
 *   — MITTEL-INSEL: die Turmruine (towerruin, 63x87x66) bei 0/0 —
 *     ~30 Bloecke Luecke zu beiden Spawn-Inseln, mit Loot-Kisten.
 *   — pro Spieler 2 NEBEN-INSELN (5x5): EISEN-Spawner (alle 20s 2x Eisen)
 *     und DIAMANT-Spawner (alle 20s 1 Diamant).
 *
 * Spawner, Haendler und Bloecke-Platzieren funktionieren IMMER (auch beim
 * Solo-Erkunden); der Kampf startet, sobald 2 Spieler da sind (3s-Freeze).
 * Tod, Void, /lobby oder Disconnect = Gegner gewinnt. Reset (gesetzte
 * Bloecke weg, Loot neu, Haendler frisch) nach jedem Match UND sobald der
 * letzte Spieler den Server verlaesst.
 */
public class SkywarsPlugin extends JavaPlugin implements Listener {

    private enum Phase { WARTEN, COUNTDOWN, KAMPF, ENDE }

    private static final int BASIS_Y = 60;

    private Phase phase = Phase.WARTEN;
    private SchematicLader spawnInsel, mitte;
    /** Dynamisches Layout — berechnet aus dem ECHTEN Terrain der Schematik
     *  (das Inselterrain sitzt ausserhalb der Box-Mitte!). */
    private int spawnX;   // Terrain-Zentren der Spawn-Inseln bei +/- spawnX
    private int nebenZ;   // Neben-Inseln bei z = +/- nebenZ
    private boolean karteFertig;
    private final Set<Location> gesetzt = new HashSet<>();
    private final List<Location> lootKisten = new ArrayList<>();
    private final List<Location> spawner = new ArrayList<>();

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

        try {
            spawnInsel = new SchematicLader(new File(getDataFolder(), "sw_spawn.dschem"));
            mitte = new SchematicLader(new File(getDataFolder(), "sw_mitte.dschem"));
            berechneLayout();
            baueKarte();
        } catch (Exception ex) {
            getLogger().severe("Karte konnte nicht geladen werden: " + ex.getMessage());
        }
        Bukkit.getScheduler().runTaskTimer(this, this::spawnerTick, 100L, 20L * 20);
    }

    private World welt() {
        return Bukkit.getWorlds().get(0);
    }

    // ────────────────────────── Terrain-Analyse ──────────────────────────

    /** Echte Terrain-Grenzen einer Schematik: [minX, maxX, minZ, maxZ, avgTop, maxTop]. */
    private int[] terrain(SchematicLader s) {
        int minX = s.breite, maxX = 0, minZ = s.laenge, maxZ = 0, maxTop = 0;
        long summe = 0;
        int spalten = 0;
        for (int x = 0; x < s.breite; x++) {
            for (int z = 0; z < s.laenge; z++) {
                for (int y = s.hoehe - 1; y >= 0; y--) {
                    if (s.blockAn(x, y, z) != null) {
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minZ = Math.min(minZ, z);
                        maxZ = Math.max(maxZ, z);
                        maxTop = Math.max(maxTop, y);
                        summe += y;
                        spalten++;
                        break;
                    }
                }
            }
        }
        int avgTop = spalten > 0 ? (int) (summe / spalten) : 0;
        return new int[]{minX, maxX, minZ, maxZ, avgTop, maxTop};
    }

    private int[] inselTerrain, turmTerrain;
    private int inselBasisY, turmBasisY;

    /** Layout aus dem ECHTEN Terrain berechnen (Inseln sitzen ausserhalb der Box-Mitte). */
    private void berechneLayout() {
        inselTerrain = terrain(spawnInsel);
        turmTerrain = terrain(mitte);
        int inselHalbX = (inselTerrain[1] - inselTerrain[0]) / 2;
        int inselHalbZ = (inselTerrain[3] - inselTerrain[2]) / 2;
        int turmHalbX = (turmTerrain[1] - turmTerrain[0]) / 2;

        spawnX = turmHalbX + 30 + inselHalbX;    // ~30 Bloecke Luecke zur Turmruine
        nebenZ = inselHalbZ + 18;                // ~18 Bloecke Brueckweite

        inselBasisY = BASIS_Y;
        // Turm anheben: seine Spitze soll knapp UEBER der Insel-Oberflaeche liegen
        int inselOberflaeche = BASIS_Y + inselTerrain[4];
        turmBasisY = inselOberflaeche + 8 - turmTerrain[5];

        getLogger().info("Layout: spawnX=" + spawnX + " nebenZ=" + nebenZ
            + " inselOberflaeche=y" + inselOberflaeche + " turmBasis=y" + turmBasisY);
    }

    /** Spiralsuche nach einer Spalte mit Boden (fuer Spawn, Kiste, Haendler). */
    private Location bodenNahe(int cx, int cz) {
        World w = welt();
        for (int radius = 0; radius <= 30; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int y = w.getHighestBlockYAt(cx + dx, cz + dz);
                    if (y > w.getMinHeight()) {
                        return new Location(w, cx + dx + 0.5, y + 1, cz + dz + 0.5);
                    }
                }
            }
        }
        return new Location(w, cx + 0.5, BASIS_Y + 100, cz + 0.5);
    }

    /** Spawn-Punkt: begehbarer Boden nahe dem Terrain-Zentrum der Insel. */
    private Location spawnOrt(boolean links) {
        Location l = bodenNahe(links ? -spawnX : spawnX, 0);
        l.setYaw(links ? -90 : 90); // Blick zum Gegner
        return l;
    }

    // ────────────────────────── Karte bauen ──────────────────────────

    private void baueKarte() {
        File marker = new File(getDataFolder(), "karte-gebaut.marker");
        if (marker.exists()) {
            karteFertig = true;
            richteEin();
            getLogger().info("SkyWars-Karte vorhanden — Einrichtung erneuert.");
            return;
        }
        getLogger().info("Baue SkyWars-Karte (2x Insel + Turmruine)...");
        List<Location> orte = new ArrayList<>();
        List<BlockData> daten = new ArrayList<>();
        World w = welt();
        // Beide Spawn-Inseln — so verschoben, dass das TERRAIN-Zentrum bei +/-spawnX liegt
        int terrainMitteX = (inselTerrain[0] + inselTerrain[1]) / 2;
        int terrainMitteZ = (inselTerrain[2] + inselTerrain[3]) / 2;
        for (int seite : new int[]{-1, 1}) {
            sammle(spawnInsel, w, seite * spawnX - terrainMitteX, inselBasisY, -terrainMitteZ, orte, daten);
        }
        // Turmruine mittig (Terrain-Zentrum auf 0/0), angehoben auf Insel-Niveau
        int turmMitteX = (turmTerrain[0] + turmTerrain[1]) / 2;
        int turmMitteZ = (turmTerrain[2] + turmTerrain[3]) / 2;
        sammle(mitte, w, -turmMitteX, turmBasisY, -turmMitteZ, orte, daten);

        final int gesamt = orte.size();
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            int budget = 4000;
            while (budget-- > 0 && index[0] < gesamt) {
                int i = index[0]++;
                orte.get(i).getBlock().setBlockData(daten.get(i), false);
            }
            if (index[0] >= gesamt) {
                task.cancel();
                karteFertig = true;
                richteEin();
                try {
                    marker.createNewFile();
                } catch (Exception ignored) { }
                getLogger().info("Karte fertig: " + gesamt + " Bloecke.");
            }
        }, 1L, 1L);
    }

    private void sammle(SchematicLader s, World w, int ox, int oy, int oz,
                        List<Location> orte, List<BlockData> daten) {
        for (int y = 0; y < s.hoehe; y++) {
            for (int z = 0; z < s.laenge; z++) {
                for (int x = 0; x < s.breite; x++) {
                    BlockData bd = s.blockAn(x, y, z);
                    if (bd == null) continue;
                    orte.add(new Location(w, ox + x, oy + y, oz + z));
                    daten.add(bd);
                }
            }
        }
    }

    /** Neben-Inseln, Kisten, Haendler, Spawnpunkte — nach dem Kartenbau. */
    private void richteEin() {
        World w = welt();
        lootKisten.clear();
        spawner.clear();
        int inselOberflaeche = BASIS_Y + inselTerrain[4];

        for (int seite : new int[]{-1, 1}) {
            int cx = seite * spawnX;

            // Start-Kiste auf festem Boden neben dem Spawn
            Location kistenOrt = bodenNahe(cx, 5);
            Block start = kistenOrt.getBlock();
            start.setType(Material.CHEST);
            if (start.getState() instanceof Chest c) {
                c.getInventory().clear();
                c.getInventory().addItem(
                    new ItemStack(Material.SANDSTONE, 64),
                    new ItemStack(Material.STONE_SWORD),
                    new ItemStack(Material.COOKED_BEEF, 8));
            }

            // Neben-Inseln: Eisen (z=-nebenZ), Diamant (z=+nebenZ) auf Insel-Niveau
            for (int richtung : new int[]{-1, 1}) {
                int cz = richtung * nebenZ;
                boolean diamant = richtung > 0;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        w.getBlockAt(cx + dx, inselOberflaeche, cz + dz).setType(Material.STONE);
                        w.getBlockAt(cx + dx, inselOberflaeche - 1, cz + dz).setType(Material.COBBLESTONE);
                    }
                }
                w.getBlockAt(cx, inselOberflaeche + 1, cz)
                    .setType(diamant ? Material.DIAMOND_BLOCK : Material.IRON_BLOCK);
                spawner.add(new Location(w, cx + 0.5, inselOberflaeche + 2.2, cz + 0.5));
            }
        }

        // Loot-Kisten auf der Turmruine (verschiedene Ebenen, per Bodensuche)
        for (int[] pos : new int[][]{{0, 0}, {10, 10}, {-10, -8}, {8, -10}}) {
            Location ort = bodenNahe(pos[0], pos[1]);
            Block k = ort.getBlock();
            k.setType(Material.CHEST);
            lootKisten.add(k.getLocation());
        }
        sammleAlleContainer();
        fuelleLoot();
        spawneHaendler();
        w.setSpawnLocation(spawnOrt(true));
    }

    /** Findet ALLE Kisten & Faesser auf allen Inseln (Chunk-Scan des Kartenbereichs). */
    private void sammleAlleContainer() {
        World w = welt();
        Set<Location> gefunden = new HashSet<>(lootKisten);
        int maxX = spawnX + spawnInsel.breite / 2 + 16;
        int maxZ = Math.max(nebenZ + 16, spawnInsel.laenge / 2 + 16);
        for (int cx = -maxX >> 4; cx <= maxX >> 4; cx++) {
            for (int cz = -maxZ >> 4; cz <= maxZ >> 4; cz++) {
                for (var state : w.getChunkAt(cx, cz).getTileEntities()) {
                    if (state instanceof org.bukkit.block.Container) {
                        gefunden.add(state.getLocation());
                    }
                }
            }
        }
        lootKisten.clear();
        lootKisten.addAll(gefunden);
        getLogger().info(lootKisten.size() + " Loot-Container auf der Karte gefunden.");
    }

    /** Random-Loot bis Diamant: Ruestung, Waffen, Schild, Eimer & Co. */
    private void fuelleLoot() {
        List<ItemStack> pool = List.of(
            new ItemStack(Material.IRON_INGOT, 6), new ItemStack(Material.DIAMOND, 2),
            new ItemStack(Material.GOLDEN_APPLE, 1), new ItemStack(Material.ENDER_PEARL, 1),
            new ItemStack(Material.ARROW, 12), new ItemStack(Material.SANDSTONE, 32),
            new ItemStack(Material.COOKED_BEEF, 6), new ItemStack(Material.STONE_AXE),
            new ItemStack(Material.SHIELD), new ItemStack(Material.WATER_BUCKET),
            new ItemStack(Material.LAVA_BUCKET), new ItemStack(Material.BOW),
            new ItemStack(Material.STONE_SWORD), new ItemStack(Material.IRON_SWORD),
            new ItemStack(Material.DIAMOND_SWORD),
            new ItemStack(Material.IRON_HELMET), new ItemStack(Material.IRON_CHESTPLATE),
            new ItemStack(Material.IRON_LEGGINGS), new ItemStack(Material.IRON_BOOTS),
            new ItemStack(Material.DIAMOND_HELMET), new ItemStack(Material.DIAMOND_BOOTS));
        var zufall = ThreadLocalRandom.current();
        for (Location ort : lootKisten) {
            if (!(ort.getBlock().getState() instanceof Chest kiste)) continue;
            var inv = kiste.getBlockInventory();
            inv.clear();
            for (int i = 0; i < 4 + zufall.nextInt(4); i++) {
                inv.setItem(zufall.nextInt(inv.getSize()), pool.get(zufall.nextInt(pool.size())).clone());
            }
        }
    }

    // ────────────────────────── Haendler ──────────────────────────

    private void spawneHaendler() {
        // Duplikate vermeiden
        for (Entity e : welt().getEntities()) {
            if (e instanceof Villager) e.remove();
        }
        World w = welt();
        for (int seite : new int[]{-1, 1}) {
            int cx = seite * spawnX;
            Location ort = bodenNahe(cx, -5);
            Villager v = (Villager) w.spawnEntity(ort, EntityType.VILLAGER);
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
            // UPGRADE: 12 Diamanten schalten verzauberte Trades frei
            handel.add(handel(Material.DIAMOND, 12, upgradeStern()));
            v.setRecipes(handel);
        }
    }

    private ItemStack upgradeStern() {
        ItemStack stern = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = stern.getItemMeta();
        meta.displayName(Component.text("HAENDLER-UPGRADE", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Schaltet VERZAUBERTES Gear frei!", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)));
        stern.setItemMeta(meta);
        return stern;
    }

    /** Upgrade gekauft? Dann bekommt DIESER Haendler die verzauberten Trades. */
    @EventHandler
    public void onTrade(io.papermc.paper.event.player.PlayerTradeEvent event) {
        ItemStack ergebnis = event.getTrade().getResult();
        if (ergebnis.getType() != Material.NETHER_STAR || !ergebnis.hasItemMeta()
            || !ergebnis.getItemMeta().hasDisplayName()) return;
        if (!(event.getVillager() instanceof Villager v)) return;
        Player p = event.getPlayer();

        // Verzauberte Tier-2-Trades setzen
        List<MerchantRecipe> handel = new ArrayList<>();
        handel.add(handel(Material.IRON_INGOT, 2, new ItemStack(Material.SANDSTONE, 16)));
        handel.add(handel(Material.IRON_INGOT, 4, new ItemStack(Material.COOKED_BEEF, 6)));
        handel.add(handel(Material.IRON_INGOT, 6, new ItemStack(Material.ARROW, 12)));
        handel.add(handel(Material.DIAMOND, 3, new ItemStack(Material.GOLDEN_APPLE)));
        handel.add(handel(Material.DIAMOND, 10, verzaubert(Material.DIAMOND_SWORD, Enchantment.SHARPNESS, 2)));
        handel.add(handel(Material.DIAMOND, 7, verzaubert(Material.DIAMOND_HELMET, Enchantment.PROTECTION, 2)));
        handel.add(handel(Material.DIAMOND, 11, verzaubert(Material.DIAMOND_CHESTPLATE, Enchantment.PROTECTION, 2)));
        handel.add(handel(Material.DIAMOND, 9, verzaubert(Material.DIAMOND_LEGGINGS, Enchantment.PROTECTION, 2)));
        handel.add(handel(Material.DIAMOND, 7, verzaubert(Material.DIAMOND_BOOTS, Enchantment.PROTECTION, 2)));
        handel.add(handel(Material.DIAMOND, 12, verzaubert(Material.BOW, Enchantment.POWER, 3)));
        v.setRecipes(handel);
        v.customName(Component.text("Meister-Haendler", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));

        // Der Stern selbst ist nur der Kaufbeleg — wieder einsammeln
        Bukkit.getScheduler().runTask(this, () -> {
            for (ItemStack item : p.getInventory().getContents()) {
                if (item != null && item.getType() == Material.NETHER_STAR
                    && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
                    p.getInventory().remove(item);
                }
            }
        });
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
        p.sendMessage(Component.text("✨ Dein Haendler ist jetzt MEISTER-HAENDLER — verzaubertes Gear verfuegbar!",
            NamedTextColor.LIGHT_PURPLE));
    }

    private ItemStack verzaubert(Material mat, Enchantment ench, int stufe) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(ench, stufe, true);
        item.setItemMeta(meta);
        return item;
    }

    private MerchantRecipe handel(Material waehrung, int preis, ItemStack ware) {
        MerchantRecipe r = new MerchantRecipe(ware, 999);
        r.addIngredient(new ItemStack(waehrung, preis));
        return r;
    }

    // ────────────────────────── Spawner (laufen IMMER) ──────────────────────────

    private void spawnerTick() {
        if (!karteFertig || Bukkit.getOnlinePlayers().isEmpty()) return;
        World w = welt();
        for (Location ort : spawner) {
            boolean diamant = ort.getBlockZ() > 0;
            Item item = w.dropItem(ort, diamant
                ? new ItemStack(Material.DIAMOND, 1)
                : new ItemStack(Material.IRON_INGOT, 2));
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
        p.sendMessage(Component.text("🏝 SKYWARS-DUELL! Farme Eisen & Diamanten, kauf bei DEINEM Haendler ein — "
            + "gekaempft wird auf der Turmruine!", NamedTextColor.AQUA));
        pruefeStart();
    }

    private void pruefeStart() {
        if (phase != Phase.WARTEN) return;
        List<Player> spieler = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (spieler.size() < 2) return;
        phase = Phase.COUNTDOWN;

        spieler.get(0).teleport(spawnOrt(true));
        spieler.get(1).teleport(spawnOrt(false));

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
        if (event.getTo().getY() < BASIS_Y - 20) {
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

    /** Beim Tod droppt der KOMPLETTE Loot — und der Gegner gewinnt. */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
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
            phase = Phase.WARTEN;
        }
        // Letzter Spieler weg? Einen Tick spaeter pruefen und Karte refreshen.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                resetKarte();
                phase = Phase.WARTEN;
            }
        }, 20L);
    }

    /** Fallschaden & Co. sind IMMER an — nur im Countdown/Siegerscreen nicht. */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if ((phase == Phase.COUNTDOWN || phase == Phase.ENDE)
            && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    /** Karte ist unzerstoerbar — nur selbst gesetzte Bloecke gehen weg. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!gesetzt.remove(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    /** Platzieren geht IMMER (nur nicht im Countdown) — wird protokolliert. */
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (phase == Phase.COUNTDOWN) {
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

    /** Gesetzte Bloecke weg, Items weg, Loot neu, Haendler frisch. */
    private void resetKarte() {
        for (Location ort : gesetzt) {
            ort.getBlock().setType(Material.AIR);
        }
        gesetzt.clear();
        for (Entity e : welt().getEntities()) {
            if (e instanceof Item) e.remove();
        }
        if (karteFertig) {
            fuelleLoot();
            spawneHaendler();
        }
        getLogger().info("Karte zurueckgesetzt.");
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
