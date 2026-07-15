package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.EventManager.EventType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Ingame-Sidebar mit allen wichtigen Stats — aktualisiert alle 2 Sekunden.
 * /sidebar blendet sie pro Spieler ein/aus.
 */
public class SidebarManager {

    private final HardcorePlugin plugin;
    private final Set<UUID> hidden = new HashSet<>();

    public SidebarManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 40L, 40L);
    }

    public boolean toggle(UUID player) {
        boolean nowHidden;
        if (hidden.contains(player)) {
            hidden.remove(player);
            nowHidden = false;
        } else {
            hidden.add(player);
            nowHidden = true;
        }
        Player p = Bukkit.getPlayer(player);
        if (p != null && nowHidden) {
            p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
        return !nowHidden;
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (hidden.contains(p.getUniqueId())) continue;
            update(p);
        }
    }

    private void update(Player p) {
        boolean kopfgeld = plugin.getConfig().getBoolean("kopfgeld-system", true);
        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective obj = board.registerNewObjective("doofie", Criteria.DUMMY,
            Component.text(kopfgeld ? "BOUNTY SMP" : "DOOFIESMP", NamedTextColor.GOLD, TextDecoration.BOLD));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        UUID id = p.getUniqueId();
        var guild = plugin.guilds().byMember(id);
        EventType event = plugin.events().current();

        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("§7Geld: §6" + HardcorePlugin.dollar(plugin.economy().get(id)));
        lines.add("§7Kills: §f" + plugin.stats().kills().getOrDefault(id, 0));
        lines.add("§7Gilde: §b" + (guild != null ? guild.name : "-"));
        if (kopfgeld) {
            double bounty = plugin.bounties().total(id);
            lines.add(bounty > 0
                ? "§4Kopfgeld auf dir: §c" + HardcorePlugin.dollar(bounty)
                : "§7Kopfgeld auf dir: §a-");
            lines.add("§7Joker: " + (plugin.bans().hasJoker(id) ? "§dverfuegbar" : "§8verbraucht"));
        }
        lines.add("§7Lotto-Pot: §e" + HardcorePlugin.dollar(plugin.lotto().pot()));
        lines.add("§7Event: " + (event == EventType.NONE ? "§8-" : "§c" + event.title));

        int score = lines.size();
        for (String line : lines) {
            obj.getScore(line).setScore(score--);
        }
        p.setScoreboard(board);
    }
}
