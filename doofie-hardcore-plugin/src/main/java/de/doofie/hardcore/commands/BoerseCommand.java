package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** /boerse [kaufen|verkaufen <firma> <anzahl>] — der Doofie-Aktienmarkt. */
public class BoerseCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public BoerseCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        var stocks = plugin.stocks();

        if (args.length >= 3) {
            String firm = stocks.matchFirm(args[1]);
            if (firm == null) { p.sendMessage(Component.text("Firma nicht gefunden.", NamedTextColor.RED)); return true; }
            int count;
            try { count = Math.max(1, Integer.parseInt(args[2])); } catch (NumberFormatException e) { return true; }
            double price = stocks.prices().get(firm);
            String sub = args[0].toLowerCase(Locale.ROOT);

            if (sub.equals("kaufen")) {
                double cost = price * count;
                if (!plugin.economy().withdraw(p.getUniqueId(), cost)) {
                    p.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(cost) + ")", NamedTextColor.RED));
                    return true;
                }
                stocks.buy(p.getUniqueId(), firm, count);
                p.sendMessage(Component.text(count + "x " + firm + " gekauft fuer " + HardcorePlugin.dollar(cost), NamedTextColor.GREEN));
            } else if (sub.equals("verkaufen")) {
                if (!stocks.sell(p.getUniqueId(), firm, count)) {
                    p.sendMessage(Component.text("So viele Aktien hast du nicht.", NamedTextColor.RED));
                    return true;
                }
                double gain = price * count;
                plugin.economy().deposit(p.getUniqueId(), gain);
                p.sendMessage(Component.text(count + "x " + firm + " verkauft fuer " + HardcorePlugin.dollar(gain), NamedTextColor.GREEN));
            }
            return true;
        }

        p.sendMessage(Component.text("── Doofie-Boerse (Kurse alle 10 Min.) ──", NamedTextColor.GOLD));
        stocks.prices().forEach((firm, price) -> {
            double trend = stocks.trend(firm);
            String arrow = trend > 0.5 ? "steigt" : trend < -0.5 ? "faellt" : "stabil";
            int owned = stocks.owned(p.getUniqueId(), firm);
            p.sendMessage(Component.text("  " + firm + " — " + HardcorePlugin.dollar(price)
                + " [" + arrow + " " + String.format(Locale.GERMAN, "%.1f", Math.abs(trend)) + "%]"
                + (owned > 0 ? " | du: " + owned + "x" : ""),
                trend > 0.5 ? NamedTextColor.GREEN : trend < -0.5 ? NamedTextColor.RED : NamedTextColor.GRAY));
        });
        p.sendMessage(Component.text("/boerse kaufen <firma> <anzahl> | /boerse verkaufen <firma> <anzahl>", NamedTextColor.GRAY));
        return true;
    }
}
