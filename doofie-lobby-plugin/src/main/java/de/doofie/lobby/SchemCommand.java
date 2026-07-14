package de.doofie.lobby;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * /schemplatz <datei> — platziert eine WorldEdit-Schematic (.schem,
 * Sponge-Format v2/v3) an deiner Position. Admin-only.
 *
 * Die Dateien liegen in plugins/DoofieLobby/schematics/.
 * Eigener Mini-NBT-Parser — kein WorldEdit noetig. Die Bloecke werden
 * gebatcht gesetzt (2000 pro Tick), damit der Server nicht ruckelt.
 * Grenzen: Kisten-Inhalte/Schilder-Texte (BlockEntities) werden nicht
 * uebernommen, nur die Bloecke selbst.
 */
public class SchemCommand implements TabExecutor {

    private final JavaPlugin plugin;

    public SchemCommand(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        new File(plugin.getDataFolder(), "schematics").mkdirs();
        plugin.getCommand("schemplatz").setExecutor(this);
        plugin.getCommand("schemplatz").setTabCompleter(this);
    }

    // ────────────────────────── Mini-NBT-Parser ──────────────────────────

    /** Liest einen NBT-Tag-Payload; Compounds als Map, Listen als List. */
    private static Object lesePayload(DataInputStream in, int typ) throws IOException {
        return switch (typ) {
            case 1 -> in.readByte();
            case 2 -> in.readShort();
            case 3 -> in.readInt();
            case 4 -> in.readLong();
            case 5 -> in.readFloat();
            case 6 -> in.readDouble();
            case 7 -> { // byte array
                byte[] b = new byte[in.readInt()];
                in.readFully(b);
                yield b;
            }
            case 8 -> in.readUTF();
            case 9 -> { // list
                int elTyp = in.readByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<>(len);
                for (int i = 0; i < len; i++) list.add(lesePayload(in, elTyp));
                yield list;
            }
            case 10 -> { // compound
                Map<String, Object> map = new HashMap<>();
                int t;
                while ((t = in.readByte()) != 0) {
                    String name = in.readUTF();
                    map.put(name, lesePayload(in, t));
                }
                yield map;
            }
            case 11 -> { // int array
                int[] a = new int[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                yield a;
            }
            case 12 -> { // long array
                long[] a = new long[in.readInt()];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                yield a;
            }
            default -> throw new IOException("Unbekannter NBT-Tag: " + typ);
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> leseNbt(File datei) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new FileInputStream(datei)))) {
            if (in.readByte() != 10) throw new IOException("Kein NBT-Compound am Anfang");
            in.readUTF(); // Root-Name
            return (Map<String, Object>) lesePayload(in, 10);
        }
    }

    // ────────────────────────── Platzieren ──────────────────────────

    @SuppressWarnings("unchecked")
    private void platziere(Player p, File datei) throws IOException {
        Map<String, Object> root = leseNbt(datei);
        // Sponge v3 verschachtelt alles unter "Schematic", v2 liegt flach
        if (root.get("Schematic") instanceof Map<?, ?> inner) {
            root = (Map<String, Object>) inner;
        }
        int breite = ((Number) root.get("Width")).intValue();
        int hoehe = ((Number) root.get("Height")).intValue();
        int laenge = ((Number) root.get("Length")).intValue();

        Map<String, Object> paletteTag;
        byte[] blockData;
        if (root.get("Blocks") instanceof Map<?, ?> blocks) { // v3
            paletteTag = (Map<String, Object>) blocks.get("Palette");
            blockData = (byte[]) ((Map<String, Object>) blocks).get("Data");
        } else { // v2
            paletteTag = (Map<String, Object>) root.get("Palette");
            blockData = (byte[]) root.get("BlockData");
        }

        // Palette: Index -> BlockData (ungueltige Eintraege werden Luft)
        BlockData[] palette = new BlockData[paletteTag.size()];
        for (Map.Entry<String, Object> e : paletteTag.entrySet()) {
            int id = ((Number) e.getValue()).intValue();
            try {
                palette[id] = Bukkit.createBlockData(e.getKey());
            } catch (IllegalArgumentException ex) {
                palette[id] = null;
                plugin.getLogger().warning("Unbekannter Block in Schematic: " + e.getKey());
            }
        }

        // Alle Bloecke einsammeln (Varint-Indizes in YZX-Reihenfolge)
        Location start = p.getLocation().getBlock().getLocation();
        List<Location> orte = new ArrayList<>();
        List<BlockData> daten = new ArrayList<>();
        int pos = 0;
        for (int y = 0; y < hoehe; y++) {
            for (int z = 0; z < laenge; z++) {
                for (int x = 0; x < breite; x++) {
                    int wert = 0, shift = 0;
                    byte b;
                    do {
                        b = blockData[pos++];
                        wert |= (b & 0x7F) << shift;
                        shift += 7;
                    } while ((b & 0x80) != 0);
                    BlockData bd = wert < palette.length ? palette[wert] : null;
                    if (bd == null || bd.getMaterial() == org.bukkit.Material.STRUCTURE_VOID) continue;
                    orte.add(start.clone().add(x, y, z));
                    daten.add(bd);
                }
            }
        }

        // Gebatcht setzen: 2000 Bloecke pro Tick
        final int gesamt = orte.size();
        p.sendMessage(Component.text("Platziere " + gesamt + " Bloecke ("
            + breite + "x" + hoehe + "x" + laenge + ")...", NamedTextColor.AQUA));
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            int budget = 2000;
            while (budget-- > 0 && index[0] < gesamt) {
                int i = index[0]++;
                orte.get(i).getBlock().setBlockData(daten.get(i), false);
            }
            if (index[0] >= gesamt) {
                task.cancel();
                p.sendMessage(Component.text("Fertig! " + gesamt + " Bloecke platziert.", NamedTextColor.GREEN));
            }
        }, 1L, 1L);
    }

    // ────────────────────────── Command ──────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p) || !sender.hasPermission("doofie.lobby.schem")) {
            sender.sendMessage(Component.text("Nur fuer Admins (ingame).", NamedTextColor.RED));
            return true;
        }
        File ordner = new File(plugin.getDataFolder(), "schematics");
        if (args.length < 1) {
            String[] dateien = ordner.list((d, n) -> n.endsWith(".schem"));
            p.sendMessage(Component.text("Nutzung: /schemplatz <datei> — verfuegbar: "
                + (dateien == null || dateien.length == 0 ? "keine" : String.join(", ", dateien)),
                NamedTextColor.RED));
            return true;
        }
        String name = args[0].endsWith(".schem") ? args[0] : args[0] + ".schem";
        File datei = new File(ordner, name);
        if (!datei.isFile()) {
            p.sendMessage(Component.text("Datei nicht gefunden: " + name, NamedTextColor.RED));
            return true;
        }
        try {
            platziere(p, datei);
        } catch (Exception ex) {
            p.sendMessage(Component.text("Fehler beim Lesen: " + ex.getMessage(), NamedTextColor.RED));
            plugin.getLogger().warning("Schematic-Fehler: " + ex);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 1) return List.of();
        String[] dateien = new File(plugin.getDataFolder(), "schematics")
            .list((d, n) -> n.endsWith(".schem"));
        if (dateien == null) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return java.util.Arrays.stream(dateien)
            .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
