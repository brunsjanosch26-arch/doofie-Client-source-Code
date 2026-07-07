package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** /jagd <spieler> — Kompass zeigt 60s auf ein Kopfgeld-Ziel. */
public class JagdCommand implements CommandExecutor {

    private final HardcorePlugin plugin;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public JagdCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player hunter)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        if (args.length < 1) {
            hunter.sendMessage(Component.text("Nutzung: /jagd <spieler>", NamedTextColor.RED));
            return true;
        }

        long cooldown = plugin.getConfig().getLong("jagd-cooldown-sekunden", 300);
        Long last = cooldowns.get(hunter.getUniqueId());
        if (last != null) {
            long rest = cooldown - (System.currentTimeMillis() - last) / 1000;
            if (rest > 0) {
                hunter.sendMessage(Component.text("Jagd-Cooldown: noch " + rest + "s", NamedTextColor.RED));
                return true;
            }
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            hunter.sendMessage(Component.text("Spieler nicht online.", NamedTextColor.RED));
            return true;
        }
        if (!plugin.bounties().hasBounty(target.getUniqueId())) {
            hunter.sendMessage(Component.text("Auf " + target.getName() + " gibt es kein Kopfgeld — Jagd nur auf Gesuchte!", NamedTextColor.RED));
            return true;
        }

        double cost = plugin.getConfig().getDouble("jagd-kosten", 50.0);
        if (!plugin.economy().withdraw(hunter.getUniqueId(), cost)) {
            hunter.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(cost) + ")", NamedTextColor.RED));
            return true;
        }
        cooldowns.put(hunter.getUniqueId(), System.currentTimeMillis());

        long duration = plugin.getConfig().getLong("jagd-dauer-sekunden", 60);
        hunter.sendMessage(Component.text()
            .append(Component.text("JAGD! ", NamedTextColor.DARK_RED))
            .append(Component.text("Dein Kompass zeigt " + duration + "s auf ", NamedTextColor.GRAY))
            .append(Component.text(target.getName(), NamedTextColor.GOLD))
            .build());
        target.sendMessage(Component.text("Du wirst gejagt! Ein Kopfgeldjaeger hat deine Spur aufgenommen...", NamedTextColor.DARK_RED));

        // Kompass alle 2 Sekunden aktualisieren
        long ticks = duration * 20L;
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (hunter.isOnline() && target.isOnline()) {
                hunter.setCompassTarget(target.getLocation());
            }
        }, 0L, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            task.cancel();
            if (hunter.isOnline()) {
                hunter.setCompassTarget(hunter.getWorld().getSpawnLocation());
                hunter.sendMessage(Component.text("Die Jagd ist vorbei — Kompass zeigt wieder zum Spawn.", NamedTextColor.GRAY));
            }
        }, ticks);
        return true;
    }
}
