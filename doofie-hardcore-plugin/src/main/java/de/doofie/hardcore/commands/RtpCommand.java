package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * /rtp [overworld|nether|end] — zufaelliger sicherer Teleport.
 * Nur in Dimensionen, die man schon bereist hat; gesperrt bei Kopfgeld.
 */
public class RtpCommand implements CommandExecutor, Listener {

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    /** Spieler -> bereiste Dimensionen (NORMAL/NETHER/THE_END) */
    private final Map<UUID, Set<String>> visited = new HashMap<>();
    private final Random random = new Random();

    public RtpCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "rtp-visited.yml");
        load();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        markVisited(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        markVisited(event.getPlayer());
    }

    private void markVisited(Player p) {
        Set<String> set = visited.computeIfAbsent(p.getUniqueId(), u -> new HashSet<>());
        if (set.add(p.getWorld().getEnvironment().name())) save();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }

        if (plugin.bounties().hasBounty(p.getUniqueId())) {
            p.sendMessage(Component.text("Auf dir liegt ein Kopfgeld — kein /rtp fuer Gejagte!", NamedTextColor.RED));
            return true;
        }
        long cooldown = plugin.getConfig().getLong("rtp-cooldown-sekunden", 300);
        Long last = cooldowns.get(p.getUniqueId());
        if (last != null) {
            long rest = cooldown - (System.currentTimeMillis() - last) / 1000;
            if (rest > 0) {
                p.sendMessage(Component.text("RTP-Cooldown: noch " + rest + "s", NamedTextColor.RED));
                return true;
            }
        }

        // Ziel-Dimension bestimmen
        World world = p.getWorld();
        if (args.length >= 1) {
            World.Environment env = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "overworld", "oberwelt" -> World.Environment.NORMAL;
                case "nether" -> World.Environment.NETHER;
                case "end" -> World.Environment.THE_END;
                default -> null;
            };
            if (env == null) {
                p.sendMessage(Component.text("Nutzung: /rtp [overworld|nether|end]", NamedTextColor.RED));
                return true;
            }
            Set<String> seen = visited.getOrDefault(p.getUniqueId(), Set.of());
            if (!seen.contains(env.name())) {
                p.sendMessage(Component.text("Du warst noch nie in dieser Dimension — erst selbst hinreisen!", NamedTextColor.RED));
                return true;
            }
            world = Bukkit.getWorlds().stream()
                .filter(w -> w.getEnvironment() == env).findFirst().orElse(null);
            if (world == null) {
                p.sendMessage(Component.text("Diese Dimension existiert auf dem Server nicht.", NamedTextColor.RED));
                return true;
            }
        }

        int radius = plugin.getConfig().getInt("rtp-radius", 2000);
        Location target = findSafe(world, radius);
        if (target == null) {
            p.sendMessage(Component.text("Kein sicherer Ort gefunden — versuch es nochmal!", NamedTextColor.RED));
            return true;
        }
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis());
        p.teleport(target);
        p.sendMessage(Component.text("Teleportiert nach " + target.getBlockX() + " / "
            + target.getBlockY() + " / " + target.getBlockZ()
            + " (" + world.getEnvironment().name() + ") — viel Glueck!", NamedTextColor.GREEN));
        return true;
    }

    private Location findSafe(World world, int radius) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int x = random.nextInt(radius * 2) - radius;
            int z = random.nextInt(radius * 2) - radius;

            if (world.getEnvironment() == World.Environment.NETHER) {
                for (int y = 100; y > 32; y--) {
                    Material ground = world.getBlockAt(x, y - 1, z).getType();
                    if (ground.isSolid() && !ground.name().contains("MAGMA")
                        && world.getBlockAt(x, y, z).getType().isAir()
                        && world.getBlockAt(x, y + 1, z).getType().isAir()) {
                        return new Location(world, x + 0.5, y, z + 0.5);
                    }
                }
                continue;
            }

            int y = world.getHighestBlockYAt(x, z);
            Material ground = world.getBlockAt(x, y, z).getType();
            if (!ground.isSolid()) continue;
            if (ground == Material.LAVA || ground == Material.WATER
                || ground == Material.CACTUS || ground.name().contains("MAGMA")
                || ground.name().contains("FIRE") || ground == Material.POWDER_SNOW) continue;
            return new Location(world, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                visited.put(UUID.fromString(key), new HashSet<>(yaml.getStringList(key)));
            } catch (IllegalArgumentException ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        visited.forEach((uuid, set) -> yaml.set(uuid.toString(), set.stream().toList()));
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte rtp-visited.yml nicht speichern: " + e.getMessage());
        }
    }
}
