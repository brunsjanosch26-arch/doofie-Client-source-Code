package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Locale;

/** /lotto [kaufen [anzahl]] — stuendliche Lotterie. */
public class LottoCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public LottoCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        var lotto = plugin.lotto();

        if (args.length >= 1 && args[0].toLowerCase(Locale.ROOT).equals("kaufen")) {
            int count = 1;
            if (args.length >= 2) {
                try { count = Math.max(1, Integer.parseInt(args[1])); } catch (NumberFormatException ignored) {}
            }
            double cost = lotto.ticketPrice() * count;
            if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
                player.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(cost) + ")", NamedTextColor.RED));
                return true;
            }
            lotto.buy(player.getUniqueId(), count);
            player.sendMessage(Component.text()
                .append(Component.text("LOTTO! ", NamedTextColor.LIGHT_PURPLE))
                .append(Component.text(count + " Ticket(s) gekauft. Pot: ", NamedTextColor.GRAY))
                .append(Component.text(HardcorePlugin.dollar(lotto.pot()), NamedTextColor.GOLD))
                .build());
            return true;
        }

        player.sendMessage(Component.text()
            .append(Component.text("LOTTO — ", NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("Pot: ", NamedTextColor.GRAY))
            .append(Component.text(HardcorePlugin.dollar(lotto.pot()), NamedTextColor.GOLD))
            .append(Component.text(" | Deine Tickets: " + lotto.ticketsOf(player.getUniqueId())
                + "/" + lotto.totalTickets(), NamedTextColor.GRAY))
            .append(Component.text(" | /lotto kaufen [anzahl] — " + HardcorePlugin.dollar(lotto.ticketPrice()) + " pro Ticket. Ziehung jede Stunde!", NamedTextColor.GRAY))
            .build());
        return true;
    }
}
