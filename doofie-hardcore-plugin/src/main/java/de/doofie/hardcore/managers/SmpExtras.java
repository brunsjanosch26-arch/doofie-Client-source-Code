package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Bounty-SMP-Extras: Season-Pass (XP/Level), Blutgeld-Schulden,
 * Rache-Rabatt, Steuer-Topf (fuer den Tresor-Raub) und Friedenszonen.
 */
public class SmpExtras {

    public record Zone(String world, int x, int z, int radius) {}

    private final HardcorePlugin plugin;
    private final File file;

    private final Map<UUID, Integer> xp = new HashMap<>();
    private final Set<UUID> premium = new HashSet<>();
    private final Map<UUID, Double> debts = new HashMap<>();
    /** Opfer -> (Killer, Zeitpunkt) fuer den Rache-Rabatt */
    private final Map<UUID, Object[]> revenge = new HashMap<>();
    private final List<Zone> zones = new ArrayList<>();
    private double taxPot = 0;

    public SmpExtras(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "extras.yml");
        load();
        // Schulden-Zinsen: +10% pro Tag
        Bukkit.getScheduler().runTaskTimer(plugin,
            () -> debts.replaceAll((u, d) -> d * 1.10), 20L * 86400, 20L * 86400);
    }

    // ── Season-Pass ──
    public int level(UUID p) { return xp.getOrDefault(p, 0) / 100; }
    public int xpOf(UUID p) { return xp.getOrDefault(p, 0); }
    public boolean isPremium(UUID p) { return premium.contains(p); }
    public void setPremium(UUID p) { premium.add(p); }

    public void addXp(Player p, int amount) {
        int before = level(p.getUniqueId());
        xp.merge(p.getUniqueId(), amount, Integer::sum);
        int after = level(p.getUniqueId());
        if (after > before) {
            double reward = 100 * (after - before) * (isPremium(p.getUniqueId()) ? 3 : 1);
            plugin.economy().deposit(p.getUniqueId(), reward);
            p.sendMessage(Component.text("SEASON-PASS Level " + after + "! Belohnung: "
                + HardcorePlugin.dollar(reward) + (isPremium(p.getUniqueId()) ? " (Premium x3)" : ""), NamedTextColor.LIGHT_PURPLE));
        }
    }

    // ── Blutgeld-Schulden ──
    public double debt(UUID p) { return debts.getOrDefault(p, 0.0); }
    public void addDebt(UUID p, double amount) { debts.merge(p, amount, Double::sum); }

    /** Haelfte aller Einnahmen tilgt Schulden. @return Betrag, der beim Spieler ankommt */
    public double applyDebt(UUID p, double income) {
        double d = debt(p);
        if (d <= 0) return income;
        double pay = Math.min(d, income / 2);
        debts.put(p, d - pay);
        if (debts.get(p) <= 0.01) {
            debts.remove(p);
            Player pl = Bukkit.getPlayer(p);
            if (pl != null) pl.sendMessage(Component.text("Deine Blutgeld-Schulden sind getilgt!", NamedTextColor.GREEN));
        }
        return income - pay;
    }

    // ── Rache-Rabatt ──
    public void recordKill(UUID victim, UUID killer) {
        revenge.put(victim, new Object[]{ killer, System.currentTimeMillis() });
    }

    public boolean hasRevengeDiscount(UUID setter, UUID target) {
        Object[] r = revenge.get(setter);
        return r != null && r[0].equals(target)
            && System.currentTimeMillis() - (long) r[1] < 7L * 86400_000;
    }

    // ── Steuer-Topf (Tresor-Raub) ──
    public void addTax(double amount) { taxPot += amount; }
    public double taxPot() { return taxPot; }
    public double drainTaxPot() { double t = taxPot; taxPot = 0; return t; }

    // ── Friedenszonen ──
    public void addZone(Zone z) { zones.add(z); }
    public List<Zone> zones() { return zones; }

    public boolean inZone(Location loc) {
        for (Zone z : zones) {
            if (!loc.getWorld().getName().equals(z.world())) continue;
            int dx = loc.getBlockX() - z.x(), dz = loc.getBlockZ() - z.z();
            if (dx * dx + dz * dz <= z.radius() * z.radius()) return true;
        }
        return false;
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        taxPot = y.getDouble("taxpot", 0);
        ConfigurationSection s;
        if ((s = y.getConfigurationSection("xp")) != null)
            for (String k : s.getKeys(false)) try { xp.put(UUID.fromString(k), s.getInt(k)); } catch (Exception ignored) {}
        if ((s = y.getConfigurationSection("debts")) != null)
            for (String k : s.getKeys(false)) try { debts.put(UUID.fromString(k), s.getDouble(k)); } catch (Exception ignored) {}
        for (String k : y.getStringList("premium")) try { premium.add(UUID.fromString(k)); } catch (Exception ignored) {}
        for (String k : y.getStringList("zones")) {
            String[] p = k.split(";");
            zones.add(new Zone(p[0], Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])));
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("taxpot", taxPot);
        xp.forEach((u, v) -> y.set("xp." + u, v));
        debts.forEach((u, v) -> y.set("debts." + u, v));
        y.set("premium", premium.stream().map(UUID::toString).toList());
        y.set("zones", zones.stream().map(z -> z.world() + ";" + z.x() + ";" + z.z() + ";" + z.radius()).toList());
        try { y.save(file); } catch (IOException e) {
            plugin.getLogger().warning("Konnte extras.yml nicht speichern: " + e.getMessage());
        }
    }
}
