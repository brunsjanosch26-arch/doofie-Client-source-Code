package de.doofie.lifesteal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Verwaltet die Herzen aller Spieler (persistent in hearts.yml).
 *
 * — Start: 10 Herzen, Maximum: 20 Herzen.
 * — 0 Herzen = ELIMINIERT: Spieler wandert in den Zuschauer-Modus,
 *   bis ihn jemand mit einem Revive-Beacon per /revive zurueckholt
 *   (Wiedereinstieg mit 5 Herzen).
 */
public class HeartManager {

    public static final int START_HERZEN = 10;
    public static final int MAX_HERZEN = 20;
    public static final int REVIVE_HERZEN = 5;

    private final LifestealPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> herzen = new HashMap<>();
    private final Set<UUID> eliminiert = new HashSet<>();

    public HeartManager(LifestealPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hearts.yml");
        load();
    }

    // ────────────────────────── Persistenz ──────────────────────────

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (yml.isConfigurationSection("herzen")) {
            for (String key : yml.getConfigurationSection("herzen").getKeys(false)) {
                herzen.put(UUID.fromString(key), yml.getInt("herzen." + key));
            }
        }
        for (String key : yml.getStringList("eliminiert")) {
            eliminiert.add(UUID.fromString(key));
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, Integer> e : herzen.entrySet()) {
            yml.set("herzen." + e.getKey(), e.getValue());
        }
        yml.set("eliminiert", eliminiert.stream().map(UUID::toString).toList());
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Konnte hearts.yml nicht speichern: " + ex.getMessage());
        }
    }

    // ────────────────────────── Herzen ──────────────────────────

    public int getHerzen(UUID uuid) {
        return herzen.getOrDefault(uuid, START_HERZEN);
    }

    /** Setzt die Herzen (geklemmt auf 0..MAX) und aktualisiert die Lebensleiste. */
    public void setHerzen(UUID uuid, int anzahl) {
        int neu = Math.max(0, Math.min(MAX_HERZEN, anzahl));
        herzen.put(uuid, neu);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) applyHealth(p);
        save();
    }

    /** true, wenn der Spieler dadurch auf 0 gefallen (= eliminiert) ist. */
    public boolean removeHerz(UUID uuid) {
        setHerzen(uuid, getHerzen(uuid) - 1);
        return getHerzen(uuid) <= 0;
    }

    public void addHerz(UUID uuid) {
        setHerzen(uuid, getHerzen(uuid) + 1);
    }

    /** Ueberträgt die gespeicherten Herzen auf die Max-Health-Leiste. */
    public void applyHealth(Player p) {
        int h = Math.max(1, getHerzen(p.getUniqueId())); // 0 Herzen: Leiste nicht auf 0 setzen
        p.getAttribute(Attribute.MAX_HEALTH).setBaseValue(h * 2.0);
        if (p.getHealth() > h * 2.0) p.setHealth(h * 2.0);
    }

    // ────────────────────────── Elimination & Revive ──────────────────────────

    public boolean istEliminiert(UUID uuid) {
        return eliminiert.contains(uuid);
    }

    public Set<UUID> getEliminierte() {
        return Set.copyOf(eliminiert);
    }

    /** Spieler auf 0 Herzen: Zuschauer-Modus + Broadcast. */
    public void eliminiere(Player p) {
        eliminiert.add(p.getUniqueId());
        save();
        p.setGameMode(GameMode.SPECTATOR);
        p.sendMessage(Component.text("Du wurdest ELIMINIERT! Ein Freund kann dich mit", NamedTextColor.RED)
            .appendNewline()
            .append(Component.text("einem Revive-Beacon per /revive " + p.getName() + " zurueckholen.", NamedTextColor.RED)));
        Bukkit.broadcast(Component.text("☠ " + p.getName() + " hat alle Herzen verloren und ist jetzt Zuschauer!",
            NamedTextColor.DARK_RED));
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1f, 0.6f);
    }

    /** Belebt einen (auch offline) eliminierten Spieler wieder. */
    public boolean revive(OfflinePlayer ziel) {
        if (!eliminiert.remove(ziel.getUniqueId())) return false;
        setHerzen(ziel.getUniqueId(), REVIVE_HERZEN);
        Player online = ziel.getPlayer();
        if (online != null) {
            online.setGameMode(GameMode.SURVIVAL);
            online.teleport(online.getWorld().getSpawnLocation());
            applyHealth(online);
            online.setHealth(REVIVE_HERZEN * 2.0);
            online.playSound(online.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        }
        save();
        Bukkit.broadcast(Component.text("❤ " + ziel.getName() + " wurde wiederbelebt (" + REVIVE_HERZEN + " Herzen)!",
            NamedTextColor.GREEN));
        return true;
    }

    /** Beim Join: Leiste anwenden und ggf. Zuschauer-Modus durchsetzen. */
    public void handleJoin(Player p) {
        applyHealth(p);
        if (istEliminiert(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SPECTATOR);
        }
    }
}
