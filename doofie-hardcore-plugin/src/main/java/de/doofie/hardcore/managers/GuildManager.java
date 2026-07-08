package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Gilden mit Kasse und Kriegen (erster Clan mit 5 Kills gewinnt den Pot). */
public class GuildManager {

    public static class Guild {
        public String name;
        public UUID leader;
        public final List<UUID> members = new ArrayList<>();
        public double bank = 0;
    }

    public static class War {
        public String guildA, guildB;
        public int killsA = 0, killsB = 0;
        public double pot = 0;
    }

    public static final int WAR_KILL_TARGET = 5;

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<String, Guild> guilds = new HashMap<>();
    /** In-Memory: Einladungen (Spieler -> Gilde) und Kriegserklaerungen (Gilde -> (Gilde, Einsatz)) */
    private final Map<UUID, String> invites = new HashMap<>();
    private final Map<String, String[]> warRequests = new HashMap<>();
    private War activeWar = null;

    public GuildManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "guilds.yml");
        load();
    }

    public Guild byName(String name) {
        return guilds.get(name.toLowerCase());
    }

    public Guild byMember(UUID player) {
        return guilds.values().stream().filter(g -> g.members.contains(player)).findFirst().orElse(null);
    }

    public Map<String, Guild> all() { return guilds; }

    public Guild create(String name, UUID leader) {
        Guild g = new Guild();
        g.name = name;
        g.leader = leader;
        g.members.add(leader);
        guilds.put(name.toLowerCase(), g);
        return g;
    }

    public void disband(Guild g) {
        guilds.remove(g.name.toLowerCase());
    }

    public void invite(UUID player, String guildName) { invites.put(player, guildName.toLowerCase()); }
    public String inviteOf(UUID player) { return invites.remove(player); }

    public void requestWar(String from, String to, double stake) {
        warRequests.put(to.toLowerCase(), new String[]{ from.toLowerCase(), String.valueOf(stake) });
    }

    public String[] warRequestFor(String guild) { return warRequests.remove(guild.toLowerCase()); }

    public War war() { return activeWar; }

    public void startWar(String a, String b, double pot) {
        activeWar = new War();
        activeWar.guildA = a.toLowerCase();
        activeWar.guildB = b.toLowerCase();
        activeWar.pot = pot;
    }

    /** Kill im Krieg verbuchen; beendet den Krieg beim Erreichen des Ziels. */
    public void warKill(UUID killer, UUID victim) {
        if (activeWar == null) return;
        Guild gk = byMember(killer);
        Guild gv = byMember(victim);
        if (gk == null || gv == null) return;
        String k = gk.name.toLowerCase(), v = gv.name.toLowerCase();
        boolean kInWar = k.equals(activeWar.guildA) || k.equals(activeWar.guildB);
        boolean vInWar = v.equals(activeWar.guildA) || v.equals(activeWar.guildB);
        if (!kInWar || !vInWar || k.equals(v)) return;

        if (k.equals(activeWar.guildA)) activeWar.killsA++;
        else activeWar.killsB++;

        Bukkit.broadcast(Component.text("KRIEG: " + gk.name + " " +
            (k.equals(activeWar.guildA) ? activeWar.killsA : activeWar.killsB) + "/" + WAR_KILL_TARGET
            + " Kills gegen " + gv.name + "!", NamedTextColor.RED));

        String winner = activeWar.killsA >= WAR_KILL_TARGET ? activeWar.guildA
            : activeWar.killsB >= WAR_KILL_TARGET ? activeWar.guildB : null;
        if (winner != null) {
            Guild wg = guilds.get(winner);
            if (wg != null) wg.bank += activeWar.pot;
            Bukkit.broadcast(Component.text()
                .append(Component.text("KRIEG VORBEI! ", NamedTextColor.DARK_RED))
                .append(Component.text((wg != null ? wg.name : winner) + " gewinnt "
                    + HardcorePlugin.dollar(activeWar.pot) + " fuer die Gildenkasse!", NamedTextColor.GOLD))
                .build());
            activeWar = null;
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection sec = yaml.getConfigurationSection(key);
            if (sec == null) continue;
            try {
                Guild g = new Guild();
                g.name = sec.getString("name", key);
                g.leader = UUID.fromString(sec.getString("leader", ""));
                g.bank = sec.getDouble("bank", 0);
                for (String m : sec.getStringList("members")) g.members.add(UUID.fromString(m));
                guilds.put(key, g);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        guilds.forEach((key, g) -> {
            yaml.set(key + ".name", g.name);
            yaml.set(key + ".leader", g.leader.toString());
            yaml.set(key + ".bank", g.bank);
            yaml.set(key + ".members", g.members.stream().map(UUID::toString).toList());
        });
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte guilds.yml nicht speichern: " + e.getMessage());
        }
    }
}
