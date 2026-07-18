package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /sethome [name] — /home [name|liste|kaufen] — /delhome [name]
 * Jeder startet mit 1 Home-Slot; weitere Slots sind kaufbar (Preis verdoppelt sich).
 * Wie /rtp gesperrt, solange ein Kopfgeld auf dem Spieler liegt.
 */
public class HomeCommand implements CommandExecutor {

    private static final String DEFAULT_NAME = "home";

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, Map<String, Location>> homes = new HashMap<>();
    private final Map<UUID, Integer> slots = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public HomeCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "homes.yml");
        load();
    }

    private int slotsOf(UUID p) { return slots.getOrDefault(p, 1); }

    private double nextSlotPrice(UUID p) {
        double base = plugin.getConfig().getDouble("home-slot-preis", 5000);
        return base * Math.pow(2, slotsOf(p) - 1); // 5000, 10000, 20000, ...
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        UUID id = p.getUniqueId();
        Map<String, Location> my = homes.computeIfAbsent(id, u -> new LinkedHashMap<>());
        String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : DEFAULT_NAME;

        switch (cmd.getName().toLowerCase()) {
            case "sethome" -> {
                if (!my.containsKey(name) && my.size() >= slotsOf(id)) {
                    p.sendMessage(Component.text("Alle " + slotsOf(id) + " Home-Slots belegt! Naechster Slot: "
                            + HardcorePlugin.dollar(nextSlotPrice(id)) + " — /home kaufen", NamedTextColor.RED));
                    return true;
                }
                my.put(name, p.getLocation());
                save();
                p.sendMessage(Component.text("Home '" + name + "' gesetzt! (/home " + name + ")", NamedTextColor.GREEN));
            }
            case "delhome" -> {
                if (my.remove(name) == null) {
                    p.sendMessage(Component.text("Kein Home namens '" + name + "'. Deine Homes: /home liste", NamedTextColor.RED));
                    return true;
                }
                save();
                p.sendMessage(Component.text("Home '" + name + "' geloescht.", NamedTextColor.YELLOW));
            }
            case "home" -> {
                if (name.equals("liste")) {
                    if (my.isEmpty()) {
                        p.sendMessage(Component.text("Du hast keine Homes. Setze eins mit /sethome.", NamedTextColor.RED));
                        return true;
                    }
                    p.sendMessage(Component.text("Deine Homes (" + my.size() + "/" + slotsOf(id) + "): "
                            + String.join(", ", my.keySet()), NamedTextColor.AQUA));
                    return true;
                }
                if (name.equals("kaufen")) {
                    double price = nextSlotPrice(id);
                    if (!plugin.economy().withdraw(id, price)) {
                        p.sendMessage(Component.text("Zu wenig Geld — der naechste Slot kostet "
                                + HardcorePlugin.dollar(price), NamedTextColor.RED));
                        return true;
                    }
                    slots.put(id, slotsOf(id) + 1);
                    save();
                    p.sendMessage(Component.text("Neuer Home-Slot gekauft! Du hast jetzt " + slotsOf(id)
                            + " Slots.", NamedTextColor.GREEN));
                    return true;
                }
                Location home = my.get(name);
                if (home == null) {
                    p.sendMessage(Component.text(my.isEmpty()
                            ? "Du hast noch kein Home — setze eins mit /sethome."
                            : "Kein Home namens '" + name + "'. Deine Homes: /home liste", NamedTextColor.RED));
                    return true;
                }
                if (home.getWorld() == null) {
                    p.sendMessage(Component.text("Die Welt dieses Homes existiert nicht mehr — setze es neu.", NamedTextColor.RED));
                    return true;
                }
                if (plugin.bounties().hasBounty(id)) {
                    p.sendMessage(Component.text("Auf dir liegt ein Kopfgeld — kein /home fuer Gejagte!", NamedTextColor.RED));
                    return true;
                }
                long cooldown = plugin.getConfig().getLong("home-cooldown-sekunden", 60);
                Long last = cooldowns.get(id);
                if (last != null) {
                    long rest = cooldown - (System.currentTimeMillis() - last) / 1000;
                    if (rest > 0) {
                        p.sendMessage(Component.text("Home-Cooldown: noch " + rest + "s", NamedTextColor.RED));
                        return true;
                    }
                }
                cooldowns.put(id, System.currentTimeMillis());
                p.teleport(home);
                p.sendMessage(Component.text("Willkommen zu Hause!", NamedTextColor.GREEN));
            }
        }
        return true;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(key);
            if (s == null) continue;
            UUID id = UUID.fromString(key);
            // Altes Format (ein Home direkt unter der UUID) migrieren
            if (s.contains("welt")) {
                Location l = readLocation(s);
                if (l != null) homes.computeIfAbsent(id, u -> new LinkedHashMap<>()).put(DEFAULT_NAME, l);
                continue;
            }
            slots.put(id, s.getInt("slots", 1));
            ConfigurationSection hs = s.getConfigurationSection("homes");
            if (hs == null) continue;
            for (String hname : hs.getKeys(false)) {
                Location l = readLocation(hs.getConfigurationSection(hname));
                if (l != null) homes.computeIfAbsent(id, u -> new LinkedHashMap<>()).put(hname, l);
            }
        }
    }

    private Location readLocation(ConfigurationSection s) {
        if (s == null) return null;
        World world = Bukkit.getWorld(s.getString("welt", ""));
        if (world == null) return null;
        return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Map<String, Location>> e : homes.entrySet()) {
            String base = e.getKey().toString();
            yml.set(base + ".slots", slotsOf(e.getKey()));
            for (Map.Entry<String, Location> h : e.getValue().entrySet()) {
                Location l = h.getValue();
                if (l.getWorld() == null) continue;
                String k = base + ".homes." + h.getKey();
                yml.set(k + ".welt", l.getWorld().getName());
                yml.set(k + ".x", l.getX());
                yml.set(k + ".y", l.getY());
                yml.set(k + ".z", l.getZ());
                yml.set(k + ".yaw", l.getYaw());
                yml.set(k + ".pitch", l.getPitch());
            }
        }
        for (Map.Entry<UUID, Integer> e : slots.entrySet()) {
            yml.set(e.getKey().toString() + ".slots", e.getValue());
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte homes.yml nicht speichern: " + ex.getMessage());
        }
    }
}
