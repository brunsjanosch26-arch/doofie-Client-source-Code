package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

/** /auftrag <spieler> <betrag> — ANONYMES Kopfgeld (+20% Schweigegeld). */
public class AuftragCommand implements CommandExecutor {

    /** Fester Anonym-Absender fuer alle Auftraege. */
    private static final UUID ANONYM = new UUID(0, 0xD00F1E);

    private final HardcorePlugin plugin;

    public AuftragCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        if (args.length < 2) { p.sendMessage(Component.text("Nutzung: /auftrag <spieler> <betrag> — anonym, +20% Gebuehr", NamedTextColor.RED)); return true; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null || target.equals(p)) { p.sendMessage(Component.text("Spieler nicht gefunden.", NamedTextColor.RED)); return true; }
        double amount;
        try { amount = Double.parseDouble(args[1]); } catch (NumberFormatException e) { return true; }
        double min = plugin.getConfig().getDouble("min-kopfgeld", 100.0);
        if (amount < min) { p.sendMessage(Component.text("Mindestbetrag: " + HardcorePlugin.dollar(min), NamedTextColor.RED)); return true; }
        double cost = amount * 1.2;
        if (!plugin.economy().withdraw(p.getUniqueId(), cost)) {
            p.sendMessage(Component.text("Nicht genug Geld! Kostet " + HardcorePlugin.dollar(cost) + " (inkl. 20% Schweigegeld)", NamedTextColor.RED));
            return true;
        }
        plugin.extras().addTax(cost - amount);
        plugin.bounties().add(target.getUniqueId(), ANONYM, amount);
        p.sendMessage(Component.text("Der Auftrag ist raus. Niemand wird je erfahren, dass du es warst...", NamedTextColor.DARK_GRAY));
        Bukkit.broadcast(Component.text()
            .append(Component.text("ASSASSINEN-AUFTRAG! ", NamedTextColor.DARK_RED))
            .append(Component.text("Ein UNBEKANNTER hat " + HardcorePlugin.dollar(amount) + " auf ", NamedTextColor.GRAY))
            .append(Component.text(target.getName(), NamedTextColor.GOLD))
            .append(Component.text(" gesetzt! Gesamt: " + HardcorePlugin.dollar(plugin.bounties().total(target.getUniqueId())), NamedTextColor.RED))
            .build());
        return true;
    }
}
