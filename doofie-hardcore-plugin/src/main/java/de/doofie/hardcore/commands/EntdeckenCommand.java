package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.QuestManager;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.util.StructureSearchResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * /entdecken — Kompass auf die naechste neue Struktur (Terralith-Aera).
 * Wer sie erreicht, bekommt Geld + XP; zaehlt fuer die Entdecker-Quest.
 */
public class EntdeckenCommand implements CommandExecutor {

    /** Namespaces der portierten Struktur-Packs, die als "entdeckbar" gelten. */
    private static final Set<String> NAMESPACES = Set.of(
            "incendium", "nullscape", "mes", "mvs", "repurposed_structures",
            "towns_and_towers", "betterdeserttemples", "betterdungeons",
            "betterfortresses", "betterjungletemples", "betteroceanmonuments",
            "betterstrongholds", "betterwitchhuts", "kaisyn");

    private final HardcorePlugin plugin;
    private final File file;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** Spieler -> aktuelles Ziel */
    private final Map<UUID, Location> targets = new HashMap<>();
    private final Map<UUID, String> targetNames = new HashMap<>();
    /** Spieler -> bereits entdeckte Instanzen ("welt:x:z") */
    private final Map<UUID, Set<String>> discovered = new HashMap<>();

    public EntdeckenCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "entdeckungen.yml");
        load();
        // Alle 5 Sekunden pruefen, ob jemand sein Ziel erreicht hat
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkArrivals, 100L, 100L);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }

        long cooldown = plugin.getConfig().getLong("entdecken-cooldown-sekunden", 60);
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null) {
            long rest = cooldown - (System.currentTimeMillis() - last) / 1000;
            if (rest > 0) {
                p.sendMessage(Component.text("Entdecker-Cooldown: noch " + rest + "s", NamedTextColor.RED));
                return true;
            }
        }
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis());

        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        List<Structure> candidates = new ArrayList<>();
        registry.forEach(s -> {
            NamespacedKey key = registry.getKey(s);
            if (key != null && NAMESPACES.contains(key.getNamespace())) candidates.add(s);
        });
        if (candidates.isEmpty()) {
            p.sendMessage(Component.text("Keine entdeckbaren Strukturen registriert.", NamedTextColor.RED));
            return true;
        }

        p.sendMessage(Component.text("Suche eine Struktur in deiner Naehe...", NamedTextColor.GRAY));
        Set<String> seen = discovered.computeIfAbsent(p.getUniqueId(), u -> new HashSet<>());
        // Bis zu 8 zufaellige Kandidaten probieren (nicht jede Struktur existiert in jeder Dimension)
        for (int i = 0; i < 8; i++) {
            Structure s = candidates.get(random.nextInt(candidates.size()));
            StructureSearchResult result = p.getWorld().locateNearestStructure(p.getLocation(), s, 96, false);
            if (result == null) continue;
            Location loc = result.getLocation();
            String instanceKey = loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockZ();
            if (seen.contains(instanceKey)) continue;
            NamespacedKey key = registry.getKey(s);
            String name = key == null ? "Struktur" : key.getKey().replace('_', ' ');
            targets.put(p.getUniqueId(), loc);
            targetNames.put(p.getUniqueId(), name);
            p.setCompassTarget(loc);
            int dist = (int) Math.sqrt(flatDistSq(p.getLocation(), loc));
            p.sendMessage(Component.text("Entdeckung wartet: ", NamedTextColor.GOLD)
                    .append(Component.text(name, NamedTextColor.YELLOW))
                    .append(Component.text(" — " + dist + " Bloecke entfernt (X " + loc.getBlockX()
                            + " / Z " + loc.getBlockZ() + "). Dein Kompass zeigt hin!", NamedTextColor.GOLD)));
            return true;
        }
        p.sendMessage(Component.text("Gerade nichts Neues in Reichweite gefunden — versuch es woanders nochmal.", NamedTextColor.RED));
        return true;
    }

    private static double flatDistSq(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    private void checkArrivals() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Location target = targets.get(p.getUniqueId());
            if (target == null || !p.getWorld().equals(target.getWorld())) continue;
            if (flatDistSq(p.getLocation(), target) > 32 * 32) continue;

            targets.remove(p.getUniqueId());
            String name = targetNames.remove(p.getUniqueId());
            String instanceKey = target.getWorld().getName() + ":" + target.getBlockX() + ":" + target.getBlockZ();
            discovered.computeIfAbsent(p.getUniqueId(), u -> new HashSet<>()).add(instanceKey);
            save();

            double reward = plugin.getConfig().getDouble("entdecker-belohnung", 500);
            plugin.economy().deposit(p.getUniqueId(), reward);
            plugin.extras().addXp(p, 25);
            plugin.quests().progress(p, QuestManager.Type.DISCOVER, 1);
            p.sendMessage(Component.text("ENTDECKT: " + name + "! +" + HardcorePlugin.dollar(reward)
                    + " und 25 XP", NamedTextColor.GOLD));
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            discovered.put(UUID.fromString(key), new HashSet<>(yml.getStringList(key)));
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Set<String>> e : discovered.entrySet()) {
            yml.set(e.getKey().toString(), new ArrayList<>(e.getValue()));
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte entdeckungen.yml nicht speichern: " + ex.getMessage());
        }
    }
}
