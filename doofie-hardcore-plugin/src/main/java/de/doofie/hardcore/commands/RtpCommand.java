package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * /rtp — zufaelliger sicherer Teleport in der aktuellen Dimension
 * (nur Dimensionen, die man schon bereist hat; gesperrt bei Kopfgeld).
 */
public class RtpCommand implements CommandExecutor {

    private final HardcorePlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    public RtpCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
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

        World world = p.getWorld();
        // "Schon bereiste Dimension": /rtp geht nur in der Welt, in der man gerade steht —
        // dort ist man logischerweise schon gewesen.
        int radius = plugin.getConfig().getInt("rtp-radius", 2000);
        Location target = findSafe(world, radius);
        if (target == null) {
            p.sendMessage(Component.text("Kein sicherer Ort gefunden — versuch es nochmal!", NamedTextColor.RED));
            return true;
        }
        cooldowns.put(p.getUniqueId(), System.currentTimeMillis());
        p.teleport(target);
        p.sendMessage(Component.text("Teleportiert nach " + target.getBlockX() + " / "
            + target.getBlockY() + " / " + target.getBlockZ() + " — viel Glueck!", NamedTextColor.GREEN));
        return true;
    }

    private Location findSafe(World world, int radius) {
        for (int attempt = 0; attempt < 30; attempt++) {
            int x = random.nextInt(radius * 2) - radius;
            int z = random.nextInt(radius * 2) - radius;

            if (world.getEnvironment() == World.Environment.NETHER) {
                // Nether: von y=100 nach unten nach Luft-Luft-Boden suchen
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
}
