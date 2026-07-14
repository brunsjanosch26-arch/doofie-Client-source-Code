package de.doofie.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Die feste Skyblock-Karte: 10 Inseln (5x gross, 5x klassisch) im Ring
 * um die Spawn-Plattform — plus das RESET-SYSTEM:
 *
 * Waehrend der Session wird JEDE Aenderung protokolliert (abgebaut,
 * platziert, Eimer, Explosionen). Verlaesst der letzte Spieler den
 * Server (z.B. per /lobby), wird alles rueckgaengig gemacht, alle
 * Loot-Kisten werden neu befuellt, herumliegende Items und Mobs
 * entfernt — die naechste Gruppe findet die Karte frisch vor.
 * Auch das Inventar der Spieler wird beim Verlassen geleert.
 */
public class KartenManager implements Listener {

    private record Platzierung(SchematicLader schem, int x, int y, int z) {}

    private static final Location SPAWN_BLOCK = null; // Spawn ist (0.5, 101, 0.5)

    private final SkyblockPlugin plugin;
    private final List<Platzierung> platzierungen = new ArrayList<>();
    /** Session-Protokoll: Ort -> urspruenglicher Block (Einfuegereihenfolge) */
    private final LinkedHashMap<Location, BlockData> geaendert = new LinkedHashMap<>();
    /** Alle Loot-Kisten-Positionen der Karte */
    private final List<Location> lootKisten = new ArrayList<>();
    private final File marker;
    private boolean karteFertig;

    public KartenManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        this.marker = new File(plugin.getDataFolder(), "karte-gebaut.marker");
    }

    private World welt() {
        return Bukkit.getWorlds().get(0);
    }

    /** Spawn = oberster Block der Mittel-Insel (kein Quarz-Podest mehr). */
    public Location spawn() {
        World w = welt();
        int y = w.getHighestBlockYAt(0, 0);
        if (y <= w.getMinHeight()) y = 110; // Karte noch im Bau
        return new Location(w, 0.5, y + 1, 0.5);
    }

    // ────────────────────────── Karte bauen ──────────────────────────

    public void start() throws IOException {
        SchematicLader gross = new SchematicLader(new File(plugin.getDataFolder(), "schematics/insel_gross.dschem"));
        SchematicLader klassisch = new SchematicLader(new File(plugin.getDataFolder(), "schematics/insel_klassisch.dschem"));

        // Mittel-Insel (= Spawn-Insel) + 4 klassische bei Radius 70 + 5 grosse bei Radius 200
        platzierungen.add(new Platzierung(klassisch,
            -klassisch.breite / 2, 90, -klassisch.laenge / 2));
        for (int i = 0; i < 4; i++) {
            double wk = Math.toRadians(45 + i * 90);
            platzierungen.add(new Platzierung(klassisch,
                (int) (Math.cos(wk) * 70) - klassisch.breite / 2, 90,
                (int) (Math.sin(wk) * 70) - klassisch.laenge / 2));
        }
        for (int i = 0; i < 5; i++) {
            double wg = Math.toRadians(i * 72);
            platzierungen.add(new Platzierung(gross,
                (int) (Math.cos(wg) * 200) - gross.breite / 2, 60,
                (int) (Math.sin(wg) * 200) - gross.laenge / 2));
        }
        welt().setSpawnLocation(new Location(welt(), 0.5, 110, 0.5));

        if (marker.exists()) {
            // Karte steht schon — nur die Kisten-Positionen wieder einsammeln
            sammleKisten();
            karteFertig = true;
            plugin.getLogger().info("Skyblock-Karte vorhanden — " + lootKisten.size() + " Loot-Kisten registriert.");
        } else {
            baueKarte();
        }
    }

    /** Setzt alle 10 Inseln gebatcht (4000 Bloecke/Tick). */
    private void baueKarte() {
        plugin.getLogger().info("Baue Skyblock-Karte: 10 Inseln...");
        List<Location> orte = new ArrayList<>();
        List<BlockData> daten = new ArrayList<>();
        World w = welt();
        for (Platzierung pl : platzierungen) {
            SchematicLader s = pl.schem();
            for (int y = 0; y < s.hoehe; y++) {
                for (int z = 0; z < s.laenge; z++) {
                    for (int x = 0; x < s.breite; x++) {
                        BlockData bd = s.blockAn(x, y, z);
                        if (bd == null) continue;
                        orte.add(new Location(w, pl.x() + x, pl.y() + y, pl.z() + z));
                        daten.add(bd);
                    }
                }
            }
        }
        final int gesamt = orte.size();
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int budget = 4000;
            while (budget-- > 0 && index[0] < gesamt) {
                int i = index[0]++;
                orte.get(i).getBlock().setBlockData(daten.get(i), false);
            }
            if (index[0] >= gesamt) {
                task.cancel();
                sammleKisten();
                fuelleLoot();
                karteFertig = true;
                try {
                    marker.createNewFile();
                } catch (IOException ignored) { }
                plugin.getLogger().info("Karte fertig: " + gesamt + " Bloecke, "
                    + lootKisten.size() + " Loot-Kisten.");
            }
        }, 1L, 1L);
    }

    /** Findet alle Kisten-Positionen in den platzierten Schematics. */
    private void sammleKisten() {
        lootKisten.clear();
        for (Platzierung pl : platzierungen) {
            SchematicLader s = pl.schem();
            for (int y = 0; y < s.hoehe; y++) {
                for (int z = 0; z < s.laenge; z++) {
                    for (int x = 0; x < s.breite; x++) {
                        BlockData bd = s.blockAn(x, y, z);
                        if (bd != null && bd.getMaterial() == Material.CHEST) {
                            lootKisten.add(new Location(welt(), pl.x() + x, pl.y() + y, pl.z() + z));
                        }
                    }
                }
            }
        }
    }

    /** Befuellt alle Loot-Kisten mit zufaelligem Loot. */
    private void fuelleLoot() {
        List<ItemStack> pool = List.of(
            new ItemStack(Material.COBBLESTONE, 32), new ItemStack(Material.OAK_LOG, 12),
            new ItemStack(Material.BREAD, 8), new ItemStack(Material.IRON_INGOT, 5),
            new ItemStack(Material.GOLD_INGOT, 3), new ItemStack(Material.DIAMOND, 2),
            new ItemStack(Material.ENDER_PEARL, 2), new ItemStack(Material.WATER_BUCKET),
            new ItemStack(Material.LAVA_BUCKET), new ItemStack(Material.OAK_SAPLING, 4),
            new ItemStack(Material.MELON_SEEDS, 3), new ItemStack(Material.PUMPKIN_SEEDS, 3),
            new ItemStack(Material.ICE, 2), new ItemStack(Material.STRING, 6),
            new ItemStack(Material.ARROW, 12), new ItemStack(Material.EXPERIENCE_BOTTLE, 4));
        var zufall = ThreadLocalRandom.current();
        for (Location ort : lootKisten) {
            if (!(ort.getBlock().getState() instanceof Chest kiste)) continue;
            var inv = kiste.getBlockInventory();
            inv.clear();
            int anzahl = 4 + zufall.nextInt(4);
            for (int i = 0; i < anzahl; i++) {
                inv.setItem(zufall.nextInt(inv.getSize()), pool.get(zufall.nextInt(pool.size())).clone());
            }
        }
    }

    // ────────────────────────── Aenderungs-Protokoll ──────────────────────────

    private void merke(Location ort, BlockData original) {
        if (!karteFertig) return;
        geaendert.putIfAbsent(ort.getBlock().getLocation(), original);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        merke(event.getBlock().getLocation(), event.getBlock().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        merke(event.getBlock().getLocation(), event.getBlockReplacedState().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        merke(event.getBlock().getLocation(), event.getBlock().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        merke(event.getBlock().getLocation(), event.getBlock().getBlockData());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (var block : event.blockList()) {
            merke(block.getLocation(), block.getBlockData());
        }
    }

    /** Blaetter der Inseln zerfallen nicht — haelt das Protokoll klein. */
    @EventHandler
    public void onDecay(LeavesDecayEvent event) {
        event.setCancelled(true);
    }

    // ────────────────────────── Reset ──────────────────────────

    /** Macht ALLE Aenderungen rueckgaengig und befuellt den Loot neu. */
    public void reset() {
        List<Map.Entry<Location, BlockData>> eintraege = new ArrayList<>(geaendert.entrySet());
        geaendert.clear();
        for (int i = eintraege.size() - 1; i >= 0; i--) {
            eintraege.get(i).getKey().getBlock().setBlockData(eintraege.get(i).getValue(), false);
        }
        fuelleLoot();
        for (Entity e : welt().getEntities()) {
            if (e instanceof Item || (e instanceof LivingEntity && !(e instanceof Player))) e.remove();
        }
        plugin.getLogger().info("Karte zurueckgesetzt: " + eintraege.size() + " Bloecke wiederhergestellt.");
    }

    // ────────────────────────── Spieler-Events ──────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        p.teleport(spawn());
        if (!p.getInventory().contains(Material.COMPASS)) {
            p.getInventory().addItem(plugin.islands().inselKompass());
        }
        p.sendMessage(Component.text("Willkommen bei Skyblock-Wars! 10 Inseln warten — "
            + "aber Achtung: Verlaesst der letzte Spieler den Server, wird ALLES zurueckgesetzt!",
            NamedTextColor.AQUA));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Inventar leeren — nichts darf die Karten-Rotation ueberleben
        event.getPlayer().getInventory().clear();
        event.getPlayer().setLevel(0);
        event.getPlayer().setExp(0);
        // Letzter Spieler weg? Einen Tick spaeter zuruecksetzen.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) reset();
        }, 20L);
    }

    /** Void-Rettung: unter y=30 gehts zurueck zum Spawn. */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo().getY() < 30) {
            Player p = event.getPlayer();
            p.teleport(spawn());
            p.setFallDistance(0);
            p.sendMessage(Component.text("Die Leere hat dich wieder ausgespuckt.", NamedTextColor.GRAY));
        }
    }
}
