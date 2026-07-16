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
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
    private int inselHalbX, inselHalbZ; // Insel-Ausdehnung (fuer Spawn-Insel-Erkennung)
    private boolean karteFertig;
    private final Set<Location> gesetzt = new HashSet<>();
    private final List<Location> lootKisten = new ArrayList<>();
    private final List<Location> spawnInselKisten = new ArrayList<>();
    private final List<SpawnerPunkt> spawnerListe = new ArrayList<>();
    private final Set<Location> nuggetShopBloecke = new HashSet<>();

    /** Ein einzelner Ressourcen-Spawner: Dropstelle + Item + Countdown-Hologramm. */
    private static class SpawnerPunkt {
        final Location dropOrt;
        final Material item;
        final int menge;
        final int intervallSekunden;
        final String label;
        TextDisplay anzeige;
        int restSekunden;

        SpawnerPunkt(Location dropOrt, Material item, int menge, int intervallSekunden, String label) {
            this.dropOrt = dropOrt;
            this.item = item;
            this.menge = menge;
            this.intervallSekunden = intervallSekunden;
            this.label = label;
            this.restSekunden = intervallSekunden;
        }
    }

    /** Eine per Flood-Fill erkannte Insel innerhalb der Spawn-Insel-Schematik (Terrain-Spalten). */
    private static class Insel {
        int minX, maxX, minZ, maxZ, anzahl;

        int mitteX() { return (minX + maxX) / 2; }
        int mitteZ() { return (minZ + maxZ) / 2; }
    }

    /** Spalten-Cluster-ID je (x,z) der Spawn-Insel-Schematik; -1 = keine/zu kleine Insel. */
    private int[][] inselIdGrid;
    private Insel hauptInsel, innenInsel, aussenInsel;
    private int aussenInselId = -1;

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
        Bukkit.getScheduler().runTaskTimer(this, this::spawnerTick, 100L, 20L);
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
        inselHalbX = (inselTerrain[1] - inselTerrain[0]) / 2;
        inselHalbZ = (inselTerrain[3] - inselTerrain[2]) / 2;
        int turmHalbX = (turmTerrain[1] - turmTerrain[0]) / 2;

        spawnX = turmHalbX + 30 + inselHalbX;    // ~30 Bloecke Luecke zur Turmruine
        nebenZ = inselHalbZ + 18;                // ~18 Bloecke Brueckweite

        inselBasisY = BASIS_Y;
        // Turm anheben: seine Spitze soll knapp UEBER der Insel-Oberflaeche liegen
        int inselOberflaeche = BASIS_Y + inselTerrain[4];
        turmBasisY = inselOberflaeche + 8 - turmTerrain[5];

        getLogger().info("Layout: spawnX=" + spawnX + " nebenZ=" + nebenZ
            + " inselOberflaeche=y" + inselOberflaeche + " turmBasis=y" + turmBasisY);

        analysiereNebenInseln();
    }

    /**
     * Erkennt per Flood-Fill die einzelnen, physisch getrennten Inseln IN der
     * Spawn-Insel-Schematik (Haupt-Insel + die beiden Neben-Inseln mit den
     * Ressourcen-Spawnern). Die groesste zusammenhaengende Terrain-Flaeche ist
     * die Haupt-Insel; die naechstgroesseren sind die beiden Neben-Inseln.
     * Da beide Neben-Inseln in der Schematik auf derselben Seite (z-Richtung)
     * der Haupt-Insel liegen, wird die WEITER entfernte an der Haupt-Insel-
     * Mitte gespiegelt, damit rechts & links je eine echte Insel liegt.
     */
    private void analysiereNebenInseln() {
        SchematicLader s = spawnInsel;
        int[][] topY = new int[s.breite][s.laenge];
        for (int[] spalte : topY) java.util.Arrays.fill(spalte, -1);
        for (int x = 0; x < s.breite; x++) {
            for (int z = 0; z < s.laenge; z++) {
                for (int y = s.hoehe - 1; y >= 0; y--) {
                    if (s.blockAn(x, y, z) != null) {
                        topY[x][z] = y;
                        break;
                    }
                }
            }
        }

        inselIdGrid = new int[s.breite][s.laenge];
        for (int[] spalte : inselIdGrid) java.util.Arrays.fill(spalte, -1);
        boolean[][] besucht = new boolean[s.breite][s.laenge];
        List<Insel> inseln = new ArrayList<>();
        List<int[]> zuordnung = new ArrayList<>(); // [x,z,inselIndex] pro Spalte

        for (int x = 0; x < s.breite; x++) {
            for (int z = 0; z < s.laenge; z++) {
                if (topY[x][z] < 0 || besucht[x][z]) continue;
                Insel insel = new Insel();
                insel.minX = insel.maxX = x;
                insel.minZ = insel.maxZ = z;
                int inselIndex = inseln.size();
                java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
                stack.push(new int[]{x, z});
                besucht[x][z] = true;
                while (!stack.isEmpty()) {
                    int[] cur = stack.pop();
                    int cx = cur[0], cz = cur[1];
                    insel.anzahl++;
                    insel.minX = Math.min(insel.minX, cx);
                    insel.maxX = Math.max(insel.maxX, cx);
                    insel.minZ = Math.min(insel.minZ, cz);
                    insel.maxZ = Math.max(insel.maxZ, cz);
                    zuordnung.add(new int[]{cx, cz, inselIndex});
                    int[][] nachbarn = {{cx - 1, cz}, {cx + 1, cz}, {cx, cz - 1}, {cx, cz + 1}};
                    for (int[] n : nachbarn) {
                        int nx = n[0], nz = n[1];
                        if (nx < 0 || nx >= s.breite || nz < 0 || nz >= s.laenge) continue;
                        if (topY[nx][nz] < 0 || besucht[nx][nz]) continue;
                        besucht[nx][nz] = true;
                        stack.push(new int[]{nx, nz});
                    }
                }
                inseln.add(insel);
            }
        }

        List<Insel> sortiert = new ArrayList<>(inseln);
        sortiert.sort((a, b) -> b.anzahl - a.anzahl);
        if (sortiert.isEmpty()) {
            getLogger().warning("Keine Insel-Cluster in sw_spawn.dschem gefunden — Spawner bleiben an Fallback-Position.");
            return;
        }
        hauptInsel = sortiert.get(0);

        List<Insel> nebenKandidaten = new ArrayList<>();
        for (int i = 1; i < sortiert.size(); i++) {
            if (sortiert.get(i).anzahl >= 15) nebenKandidaten.add(sortiert.get(i));
            if (nebenKandidaten.size() == 2) break;
        }
        if (nebenKandidaten.size() < 2) {
            getLogger().warning("Nur " + nebenKandidaten.size() + " Neben-Insel(n) in der Schematik gefunden — "
                + "Spawner-Platzierung faellt auf die alte Plattform-Position zurueck.");
            hauptInsel = null;
            return;
        }
        // Naeher an der Haupt-Insel = innenInsel (bleibt an Ort & Stelle),
        // weiter entfernt = aussenInsel (wird gespiegelt, landet auf der Gegenseite).
        Insel a = nebenKandidaten.get(0), b = nebenKandidaten.get(1);
        int distA = Math.abs(a.mitteZ() - hauptInsel.mitteZ());
        int distB = Math.abs(b.mitteZ() - hauptInsel.mitteZ());
        innenInsel = distA <= distB ? a : b;
        aussenInsel = distA <= distB ? b : a;

        // inselIdGrid neu befuellen, aber nur mit den IDs 0=innen, 1=aussen (Rest bleibt -1)
        for (int[] eintrag : zuordnung) {
            Insel zugehoerig = inseln.get(eintrag[2]);
            if (zugehoerig == innenInsel) inselIdGrid[eintrag[0]][eintrag[1]] = 0;
            else if (zugehoerig == aussenInsel) inselIdGrid[eintrag[0]][eintrag[1]] = 1;
        }
        aussenInselId = 1;

        getLogger().info("Neben-Inseln erkannt: innen(" + innenInsel.anzahl + " Spalten, Mitte z="
            + innenInsel.mitteZ() + ") aussen(" + aussenInsel.anzahl + " Spalten, Mitte z="
            + aussenInsel.mitteZ() + ", wird gespiegelt).");
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

    /**
     * Wie {@link #bodenNahe}, aber fuer Kisten-Platzierung: ignoriert Kisten beim
     * Boden-Suchen (sonst wird bei jedem Neustart — richteEin() laeuft erneut —
     * eine neue Kiste auf die vorherige gestapelt) und raeumt evtl. bereits
     * gestapelte Kisten an der gefundenen Spalte weg, bevor eine frische gesetzt wird.
     */
    private Location bodenNaheFuerKiste(int cx, int cz) {
        World w = welt();
        for (int radius = 0; radius <= 30; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = cx + dx, z = cz + dz;
                    int y = w.getHighestBlockYAt(x, z);
                    while (y > w.getMinHeight()
                        && istKiste(w.getBlockAt(x, y, z).getType())) {
                        y--;
                    }
                    if (y > w.getMinHeight()) {
                        for (int cy = y + 1; cy <= y + 6; cy++) {
                            Block b = w.getBlockAt(x, cy, z);
                            if (istKiste(b.getType())) {
                                b.setType(Material.AIR);
                            } else if (b.getType() != Material.AIR) {
                                break;
                            }
                        }
                        return new Location(w, x + 0.5, y + 1, z + 0.5);
                    }
                }
            }
        }
        return new Location(w, cx + 0.5, BASIS_Y + 100, cz + 0.5);
    }

    private boolean istKiste(Material m) {
        return m == Material.CHEST || m == Material.TRAPPED_CHEST;
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
        raeumeAltesLayoutAuf(w, terrainMitteX, terrainMitteZ);
        for (int seite : new int[]{-1, 1}) {
            sammleSpawnInsel(w, seite * spawnX - terrainMitteX, inselBasisY, -terrainMitteZ, orte, daten);
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

    /**
     * Raeumt Ueberreste eines frueheren Karten-Layouts weg, BEVOR die Karte neu
     * gebaut wird: die alten kuenstlichen Neben-Insel-Plattformen (Vorgaenger-
     * Version) und — falls die "aussen"-Insel jetzt gespiegelt wird — ihre alte,
     * ungespiegelte Position, damit dort keine doppelte Insel stehen bleibt.
     */
    private void raeumeAltesLayoutAuf(World w, int terrainMitteX, int terrainMitteZ) {
        int inselOberflaeche = BASIS_Y + inselTerrain[4];
        int alterNebenZ = inselHalbZ + 18;
        for (int seite : new int[]{-1, 1}) {
            int cx = seite * spawnX;
            for (int richtung : new int[]{-1, 1}) {
                int cz = richtung * alterNebenZ;
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        for (int dy = -3; dy <= 7; dy++) {
                            w.getBlockAt(cx + dx, inselOberflaeche + dy, cz + dz).setType(Material.AIR);
                        }
                    }
                }
            }
            if (aussenInsel != null) {
                int ox = seite * spawnX - terrainMitteX;
                int oz = -terrainMitteZ;
                for (int x = aussenInsel.minX - 1; x <= aussenInsel.maxX + 1; x++) {
                    for (int z = aussenInsel.minZ - 1; z <= aussenInsel.maxZ + 1; z++) {
                        for (int y = 0; y < spawnInsel.hoehe; y++) {
                            w.getBlockAt(ox + x, inselBasisY + y, oz + z).setType(Material.AIR);
                        }
                    }
                }
            }
        }
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

    /**
     * Wie {@link #sammle}, aber fuer die Spawn-Insel-Schematik: die "aussen"-
     * Neben-Insel (siehe {@link #analysiereNebenInseln}) wird an der Z-Mitte
     * der Haupt-Insel gespiegelt, damit eine Neben-Insel rechts und die
     * andere links der Haupt-Insel liegt statt beide auf derselben Seite.
     */
    private void sammleSpawnInsel(World w, int ox, int oy, int oz,
                                  List<Location> orte, List<BlockData> daten) {
        SchematicLader s = spawnInsel;
        boolean spiegeln = hauptInsel != null && aussenInsel != null;
        int mitteZ = spiegeln ? hauptInsel.mitteZ() : 0;
        for (int y = 0; y < s.hoehe; y++) {
            for (int z = 0; z < s.laenge; z++) {
                for (int x = 0; x < s.breite; x++) {
                    BlockData bd = s.blockAn(x, y, z);
                    if (bd == null) continue;
                    int zZiel = z;
                    if (spiegeln && inselIdGrid[x][z] == aussenInselId) {
                        zZiel = 2 * mitteZ - z;
                    }
                    orte.add(new Location(w, ox + x, oy + y, oz + zZiel));
                    daten.add(bd);
                }
            }
        }
    }

    /** Neben-Inseln, Kisten, Haendler, Spawnpunkte — nach dem Kartenbau. */
    private void richteEin() {
        World w = welt();
        lootKisten.clear();
        spawnInselKisten.clear();
        loescheAlteHologramme();
        spawnerListe.clear();
        nuggetShopBloecke.clear();

        int terrainMitteX = (inselTerrain[0] + inselTerrain[1]) / 2;
        int terrainMitteZ = (inselTerrain[2] + inselTerrain[3]) / 2;

        for (int seite : new int[]{-1, 1}) {
            int cx = seite * spawnX;
            int ox = seite * spawnX - terrainMitteX;
            int oz = -terrainMitteZ;

            // Start-Kiste auf festem Boden neben dem Spawn — zaehlt als Spawn-Insel-Kiste
            Location kistenOrt = bodenNaheFuerKiste(cx, 5);
            Block start = kistenOrt.getBlock();
            start.setType(Material.CHEST);
            spawnInselKisten.add(start.getLocation());

            // Eisen/Diamant-Spawner auf den ECHTEN Schematik-Neben-Inseln (nicht auf
            // kuenstlichen Plattformen). "innen" bleibt an ihrer natuerlichen Position,
            // "aussen" wurde beim Kartenbau an der Haupt-Insel-Mitte gespiegelt (siehe
            // sammleSpawnInsel) — dadurch liegt eine Insel rechts, die andere links.
            if (hauptInsel != null && innenInsel != null && aussenInsel != null) {
                int rightSign = -seite; // seite=-1 (links-Spawn): +z ist rechts; seite=1: -z ist rechts
                int innenRelZ = innenInsel.mitteZ() - hauptInsel.mitteZ();
                boolean innenIstRechts = Integer.signum(innenRelZ) == Integer.signum(rightSign);

                int innenWeltX = ox + innenInsel.mitteX();
                int innenWeltZ = oz + innenInsel.mitteZ();
                int aussenWeltX = ox + aussenInsel.mitteX();
                int aussenWeltZ = oz + (2 * hauptInsel.mitteZ() - aussenInsel.mitteZ());

                erstelleRessourcenSpawner(innenWeltX, innenWeltZ,
                    innenIstRechts ? Material.IRON_BLOCK : Material.DIAMOND_BLOCK,
                    innenIstRechts ? Material.IRON_INGOT : Material.DIAMOND,
                    innenIstRechts ? 2 : 1, 20, innenIstRechts ? "§7Eisen" : "§bDiamant");
                erstelleRessourcenSpawner(aussenWeltX, aussenWeltZ,
                    innenIstRechts ? Material.DIAMOND_BLOCK : Material.IRON_BLOCK,
                    innenIstRechts ? Material.DIAMOND : Material.IRON_INGOT,
                    innenIstRechts ? 1 : 2, 20, innenIstRechts ? "§bDiamant" : "§7Eisen");
            }

            // Goldnugget-Spawner GENAU auf dem Spawnpunkt — kein fester Block (sonst
            // Erstickungsgefahr beim Respawn), stattdessen droppt hier regelmaessig
            // Gold, und der Boden-Block darunter dient als Kauf-Punkt fuer Bloecke.
            Location spawnPunkt = spawnOrt(seite == -1);
            erstelleRessourcenSpawner(spawnPunkt.getBlockX(), spawnPunkt.getBlockZ(),
                null, Material.GOLD_NUGGET, 4, 15, "§6Gold");
            Block kaufBoden = w.getBlockAt(spawnPunkt.getBlockX(), spawnPunkt.getBlockY() - 1, spawnPunkt.getBlockZ());
            nuggetShopBloecke.add(kaufBoden.getLocation());
        }

        // Loot-Kisten auf der Turmruine (verschiedene Ebenen, per Bodensuche)
        for (int[] pos : new int[][]{{0, 0}, {10, 10}, {-10, -8}, {8, -10}}) {
            Location ort = bodenNaheFuerKiste(pos[0], pos[1]);
            Block k = ort.getBlock();
            k.setType(Material.CHEST);
            lootKisten.add(k.getLocation());
        }
        sammleAlleContainer();
        fuelleLoot();
        spawneHaendler();
        w.setSpawnLocation(spawnOrt(true));
    }

    /** Entfernt Hologramme aus einem frueheren richteEin()-Lauf (z.B. nach Plugin-Reload). */
    private void loescheAlteHologramme() {
        for (Entity e : welt().getEntities()) {
            if (e instanceof TextDisplay) e.remove();
        }
    }

    /**
     * Platziert einen Ressourcen-Spawner an der Boden-Saeule bei (cx,cz): raeumt
     * einen kleinen Schacht ueber der ECHTEN Insel-Oberflaeche frei (damit keine
     * Deko-Bloecke aus der Schematik den Spawner verdecken), setzt optional einen
     * Marker-Block und ein Countdown-Hologramm.
     */
    private void erstelleRessourcenSpawner(int cx, int cz, Material markerBlock,
                                           Material item, int menge, int intervallSekunden, String label) {
        World w = welt();
        Location boden = bodenNahe(cx, cz);
        int oberflaecheY = boden.getBlockY() - 1;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 1; dy <= 8; dy++) {
                    w.getBlockAt(cx + dx, oberflaecheY + dy, cz + dz).setType(Material.AIR);
                }
            }
        }
        if (markerBlock != null) {
            w.getBlockAt(cx, oberflaecheY + 1, cz).setType(markerBlock);
        }

        Location dropOrt = new Location(w, cx + 0.5, oberflaecheY + (markerBlock != null ? 2.2 : 1.7), cz + 0.5);
        SpawnerPunkt sp = new SpawnerPunkt(dropOrt, item, menge, intervallSekunden, label);
        sp.anzeige = erzeugeHologramm(new Location(w, cx + 0.5, oberflaecheY + (markerBlock != null ? 3.4 : 2.6), cz + 0.5));
        spawnerListe.add(sp);
    }

    private TextDisplay erzeugeHologramm(Location ort) {
        return welt().spawn(ort, TextDisplay.class, e -> {
            e.setBillboard(Display.Billboard.CENTER);
            e.setShadowed(true);
            e.setSeeThrough(false);
            e.setPersistent(true);
            e.setBackgroundColor(org.bukkit.Color.fromARGB(120, 0, 0, 0));
        });
    }

    /** Ist der Ort horizontal auf einer der beiden Spawn-Inseln? */
    private boolean aufSpawnInsel(Location ort) {
        int dxLinks = Math.abs(ort.getBlockX() + spawnX);
        int dxRechts = Math.abs(ort.getBlockX() - spawnX);
        int dz = Math.abs(ort.getBlockZ());
        return (dxLinks <= inselHalbX + 4 || dxRechts <= inselHalbX + 4) && dz <= inselHalbZ + 4;
    }

    /** Findet ALLE Kisten & Faesser auf allen Inseln (Chunk-Scan des Kartenbereichs)
     *  und sortiert sie in Spawn-Insel- vs. sonstige Container ein. */
    private void sammleAlleContainer() {
        World w = welt();
        Set<Location> gefunden = new HashSet<>();
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
        for (Location ort : gefunden) {
            if (spawnInselKisten.contains(ort) || lootKisten.contains(ort)) continue;
            if (aufSpawnInsel(ort)) spawnInselKisten.add(ort);
            else lootKisten.add(ort);
        }
        getLogger().info((lootKisten.size() + spawnInselKisten.size()) + " Loot-Container gefunden ("
            + spawnInselKisten.size() + " auf Spawn-Inseln).");
    }

    /**
     * Random-Loot bis Diamant: Ruestung, Waffen, Schild, Eimer & Co.
     * Spawn-Insel-Kisten bekommen GARANTIERT (100%) 64 Black Concrete
     * plus ein paar zufaellige Items obendrauf.
     */
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

        for (Location ort : spawnInselKisten) {
            if (!(ort.getBlock().getState() instanceof Chest kiste)) continue;
            var inv = kiste.getBlockInventory();
            inv.clear();
            inv.setItem(0, new ItemStack(Material.BLACK_CONCRETE, 64)); // 100% garantiert
            for (int i = 0; i < 2 + zufall.nextInt(3); i++) {
                int slot;
                do {
                    slot = zufall.nextInt(inv.getSize());
                } while (slot == 0);
                inv.setItem(slot, pool.get(zufall.nextInt(pool.size())).clone());
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

    // ────────────────────────── Spawner (laufen IMMER, 1x/Sekunde) ──────────────────────────

    /** Laeuft jede Sekunde: zaehlt jeden Spawner runter, aktualisiert sein Countdown-
     *  Hologramm und droppt bei Ablauf das Item. */
    private void spawnerTick() {
        if (!karteFertig || Bukkit.getOnlinePlayers().isEmpty()) return;
        World w = welt();
        for (SpawnerPunkt sp : spawnerListe) {
            sp.restSekunden--;
            if (sp.restSekunden <= 0) {
                sp.restSekunden = sp.intervallSekunden;
                Item item = w.dropItem(sp.dropOrt, new ItemStack(sp.item, sp.menge));
                item.setVelocity(new org.bukkit.util.Vector(0, 0.1, 0));
                w.playSound(sp.dropOrt, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f,
                    sp.item == Material.DIAMOND ? 1.8f : sp.item == Material.GOLD_NUGGET ? 1.5f : 1.2f);
            }
            if (sp.anzeige != null && sp.anzeige.isValid()) {
                sp.anzeige.text(Component.text(sp.label + " in " + sp.restSekunden + "s", NamedTextColor.WHITE));
            }
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

    private static final int NUGGET_SHOP_KOSTEN = 4;
    private static final int NUGGET_SHOP_MENGE = 8;

    /** Rechtsklick auf den Boden-Block am eigenen Spawnpunkt: 4 Goldnuggets -> 8 Sandstein. */
    @EventHandler
    public void onNuggetShop(PlayerInteractEvent event) {
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Block clicked = event.getClickedBlock();
        if (clicked == null || !nuggetShopBloecke.contains(clicked.getLocation())) return;
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (!entferneNuggets(p, NUGGET_SHOP_KOSTEN)) {
            p.sendMessage(Component.text("Du brauchst " + NUGGET_SHOP_KOSTEN + " Goldnuggets dafuer!", NamedTextColor.RED));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        p.getInventory().addItem(new ItemStack(Material.SANDSTONE, NUGGET_SHOP_MENGE));
        p.sendMessage(Component.text("✔ " + NUGGET_SHOP_MENGE + "x Sandstein gekauft!", NamedTextColor.GOLD));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1.4f);
    }

    /** Entfernt insgesamt {@code menge} Goldnuggets aus dem Inventar — nur wenn genug da sind. */
    private boolean entferneNuggets(Player p, int menge) {
        int vorhanden = 0;
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && item.getType() == Material.GOLD_NUGGET) vorhanden += item.getAmount();
        }
        if (vorhanden < menge) return false;
        int rest = menge;
        for (ItemStack item : p.getInventory().getContents()) {
            if (rest <= 0) break;
            if (item == null || item.getType() != Material.GOLD_NUGGET) continue;
            int abzug = Math.min(rest, item.getAmount());
            item.setAmount(item.getAmount() - abzug);
            rest -= abzug;
        }
        return true;
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
