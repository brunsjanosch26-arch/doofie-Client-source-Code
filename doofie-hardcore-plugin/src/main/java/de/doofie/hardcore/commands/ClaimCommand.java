package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.GuildManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Gilden-Territorien: /claim — /unclaim — /claiminfo
 * Ein Claim = 1 Chunk, gehoert der Gilde; Fremde koennen dort nicht bauen/abbauen.
 */
public class ClaimCommand implements CommandExecutor, Listener {

    private final HardcorePlugin plugin;
    private final File file;
    /** "welt:cx:cz" -> Gildenname (lowercase) */
    private final Map<String, String> claims = new HashMap<>();

    public ClaimCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "claims.yml");
        load();
    }

    private static String keyOf(Chunk c) {
        return c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
    }

    private long claimsOf(String guildName) {
        return claims.values().stream().filter(g -> g.equals(guildName)).count();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        Chunk chunk = p.getLocation().getChunk();
        String key = keyOf(chunk);
        String owner = claims.get(key);
        GuildManager.Guild guild = plugin.guilds().byMember(p.getUniqueId());

        switch (cmd.getName().toLowerCase()) {
            case "claiminfo" -> {
                if (owner == null) {
                    p.sendMessage(Component.text("Dieser Chunk gehoert niemandem.", NamedTextColor.GRAY));
                } else {
                    p.sendMessage(Component.text("Dieser Chunk gehoert der Gilde '" + owner + "'.", NamedTextColor.AQUA));
                }
            }
            case "claim" -> {
                if (guild == null) {
                    p.sendMessage(Component.text("Du brauchst eine Gilde, um Land zu claimen (/gilde).", NamedTextColor.RED));
                    return true;
                }
                if (owner != null) {
                    p.sendMessage(Component.text("Dieser Chunk gehoert schon der Gilde '" + owner + "'.", NamedTextColor.RED));
                    return true;
                }
                int limit = plugin.getConfig().getInt("claim-limit", 16);
                String gname = guild.name.toLowerCase();
                if (claimsOf(gname) >= limit) {
                    p.sendMessage(Component.text("Eure Gilde hat schon " + limit + " Claims (Maximum).", NamedTextColor.RED));
                    return true;
                }
                double price = plugin.getConfig().getDouble("claim-preis", 2500);
                if (!plugin.economy().withdraw(p.getUniqueId(), price)) {
                    p.sendMessage(Component.text("Ein Claim kostet " + HardcorePlugin.dollar(price), NamedTextColor.RED));
                    return true;
                }
                claims.put(key, gname);
                save();
                p.sendMessage(Component.text("Chunk geclaimt fuer '" + guild.name + "'! ("
                        + claimsOf(gname) + "/" + limit + ")", NamedTextColor.GREEN));
            }
            case "unclaim" -> {
                if (guild == null || owner == null || !owner.equals(guild.name.toLowerCase())) {
                    p.sendMessage(Component.text("Dieser Chunk gehoert nicht deiner Gilde.", NamedTextColor.RED));
                    return true;
                }
                if (!guild.leader.equals(p.getUniqueId())) {
                    p.sendMessage(Component.text("Nur der Gildenleiter kann Claims aufgeben.", NamedTextColor.RED));
                    return true;
                }
                claims.remove(key);
                save();
                p.sendMessage(Component.text("Claim aufgegeben.", NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    private boolean blocked(Player p, Chunk chunk) {
        String owner = claims.get(keyOf(chunk));
        if (owner == null) return false;
        if (p.hasPermission("doofie.invsee")) return false; // Admins duerfen immer
        GuildManager.Guild guild = plugin.guilds().byMember(p.getUniqueId());
        return guild == null || !guild.name.toLowerCase().equals(owner);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Gilden-Territorium — hier darfst du nicht abbauen!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (blocked(event.getPlayer(), event.getBlock().getChunk())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Gilden-Territorium — hier darfst du nicht bauen!", NamedTextColor.RED));
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            claims.put(key.replace('|', ':'), yml.getString(key));
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, String> e : claims.entrySet()) {
            yml.set(e.getKey().replace(':', '|'), e.getValue());
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte claims.yml nicht speichern: " + ex.getMessage());
        }
    }
}
