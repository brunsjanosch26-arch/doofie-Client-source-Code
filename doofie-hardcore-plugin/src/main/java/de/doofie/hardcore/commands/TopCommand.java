package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** /top geld|kills|kopfgelder — Ranglisten. */
public class TopCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public TopCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "geld";

        switch (mode) {
            case "geld", "money" -> show(sender, "Reichste Spieler", plugin.economy().allBalances(),
                v -> HardcorePlugin.dollar(v));
            case "kills" -> show(sender, "Meiste Spieler-Kills", plugin.stats().kills(),
                v -> v.intValue() + " Kills");
            case "kopfgelder", "bounties" -> {
                sender.sendMessage(Component.text("── Hoechste Kopfgelder ──", NamedTextColor.DARK_RED));
                plugin.bounties().all().keySet().stream()
                    .sorted(Comparator.comparingDouble((UUID u) -> plugin.bounties().total(u)).reversed())
                    .limit(10)
                    .forEach(uuid -> sender.sendMessage(line(uuid, HardcorePlugin.dollar(plugin.bounties().total(uuid)))));
            }
            default -> sender.sendMessage(Component.text("Nutzung: /top <geld|kills|kopfgelder>", NamedTextColor.RED));
        }
        return true;
    }

    private <N extends Number> void show(CommandSender sender, String title, Map<UUID, N> data, Function<Double, String> format) {
        sender.sendMessage(Component.text("── " + title + " ──", NamedTextColor.GOLD));
        data.entrySet().stream()
            .sorted(Map.Entry.<UUID, N>comparingByValue(Comparator.comparingDouble(Number::doubleValue)).reversed())
            .limit(10)
            .forEach(e -> sender.sendMessage(line(e.getKey(), format.apply(e.getValue().doubleValue()))));
    }

    private Component line(UUID uuid, String value) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return Component.text()
            .append(Component.text("  " + (name != null ? name : "?"), NamedTextColor.YELLOW))
            .append(Component.text(" — " + value, NamedTextColor.WHITE))
            .build();
    }
}
