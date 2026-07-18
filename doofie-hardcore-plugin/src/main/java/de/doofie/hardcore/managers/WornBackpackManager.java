package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rucksack-Rueckenslot: /rucksack traegt den Rucksack aus der Haupthand
 * sichtbar auf dem Ruecken — Brustplatte bleibt gleichzeitig nutzbar.
 * Nochmal /rucksack nimmt ihn wieder in die Hand.
 */
public class WornBackpackManager implements CommandExecutor, Listener {

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, ItemStack> worn = new HashMap<>();
    private final Map<UUID, ItemDisplay> displays = new HashMap<>();

    public WornBackpackManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "getragene-rucksaecke.yml");
        load();
        // Position jede Tick-Runde nachfuehren (teleportDuration glaettet die Bewegung)
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    /** BackpackPlus-Items erkennen (PersistentData im backpackplus-Namespace). */
    private boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .anyMatch(k -> k.getNamespace().equalsIgnoreCase("backpackplus"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        UUID id = p.getUniqueId();

        if (worn.containsKey(id)) {
            ItemStack item = worn.remove(id);
            removeDisplay(id);
            save();
            var leftover = p.getInventory().addItem(item);
            leftover.values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            p.sendMessage(Component.text("Rucksack abgenommen.", NamedTextColor.YELLOW));
            return true;
        }

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isBackpack(hand)) {
            p.sendMessage(Component.text("Nimm einen Rucksack in die Haupthand — /rucksack traegt ihn auf dem Ruecken.", NamedTextColor.RED));
            return true;
        }
        worn.put(id, hand.clone());
        p.getInventory().setItemInMainHand(null);
        spawnDisplay(p, worn.get(id));
        save();
        p.sendMessage(Component.text("Rucksack aufgesetzt! Nochmal /rucksack, um ihn abzunehmen.", NamedTextColor.GREEN));
        return true;
    }

    private void spawnDisplay(Player p, ItemStack item) {
        removeDisplay(p.getUniqueId());
        ItemDisplay display = p.getWorld().spawn(backLocation(p), ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setBillboard(Display.Billboard.FIXED);
            d.setTeleportDuration(2);
            d.setPersistent(false);
            d.setTransformation(new Transformation(
                    new Vector3f(0f, 0f, 0f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(0.6f, 0.6f, 0.6f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)));
        });
        displays.put(p.getUniqueId(), display);
    }

    private void removeDisplay(UUID id) {
        ItemDisplay d = displays.remove(id);
        if (d != null && d.isValid()) d.remove();
    }

    /** Position knapp hinter dem Ruecken, Blickrichtung nach aussen. */
    private Location backLocation(Player p) {
        float yaw = p.getBodyYaw();
        double rad = Math.toRadians(yaw);
        // Blickvektor = (-sin, 0, cos); dahinter = Gegenrichtung
        double dx = Math.sin(rad) * 0.35;
        double dz = -Math.cos(rad) * 0.35;
        Location loc = p.getLocation().add(dx, 1.05, dz);
        loc.setYaw(yaw);
        loc.setPitch(0);
        return loc;
    }

    private void tick() {
        for (Map.Entry<UUID, ItemDisplay> e : displays.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            ItemDisplay d = e.getValue();
            if (p == null || !p.isOnline() || !d.isValid()) continue;
            if (!d.getWorld().equals(p.getWorld())) {
                spawnDisplay(p, worn.get(e.getKey()));
                continue;
            }
            d.teleport(backLocation(p));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ItemStack item = worn.get(event.getPlayer().getUniqueId());
        if (item != null) spawnDisplay(event.getPlayer(), item);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeDisplay(event.getPlayer().getUniqueId());
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                byte[] bytes = Base64.getDecoder().decode(yml.getString(key, ""));
                worn.put(UUID.fromString(key), ItemStack.deserializeBytes(bytes));
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, ItemStack> e : worn.entrySet()) {
            yml.set(e.getKey().toString(), Base64.getEncoder().encodeToString(e.getValue().serializeAsBytes()));
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte getragene-rucksaecke.yml nicht speichern: " + ex.getMessage());
        }
    }
}
