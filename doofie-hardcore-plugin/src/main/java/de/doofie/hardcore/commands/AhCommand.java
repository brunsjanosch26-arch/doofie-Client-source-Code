package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.listeners.AuctionGuiListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * /ah — Auktionshaus-GUI oeffnen
 * /ah sell <preis> — Item in der Hand einstellen
 */
public class AhCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public AhCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }

        if (args.length >= 1 && args[0].toLowerCase(Locale.ROOT).equals("sell")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Nutzung: /ah sell <preis>", NamedTextColor.RED));
                return true;
            }
            double price;
            try {
                price = Double.parseDouble(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage(Component.text("Ungueltiger Preis.", NamedTextColor.RED));
                return true;
            }
            if (price <= 0) {
                player.sendMessage(Component.text("Der Preis muss positiv sein.", NamedTextColor.RED));
                return true;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            if (hand.getType().isAir()) {
                player.sendMessage(Component.text("Du hast nichts in der Hand.", NamedTextColor.RED));
                return true;
            }
            int max = plugin.getConfig().getInt("max-auktionen-pro-spieler", 5);
            if (plugin.auctions().countBySeller(player.getUniqueId()) >= max) {
                player.sendMessage(Component.text("Du hast schon " + max + " Auktionen laufen.", NamedTextColor.RED));
                return true;
            }

            plugin.auctions().add(player.getUniqueId(), player.getName(), price, hand);
            player.getInventory().setItemInMainHand(null);
            player.sendMessage(Component.text()
                .append(Component.text("Eingestellt! ", NamedTextColor.GREEN))
                .append(Component.text("Dein Item ist fuer ", NamedTextColor.GRAY))
                .append(Component.text(HardcorePlugin.dollar(price), NamedTextColor.GOLD))
                .append(Component.text(" im /ah.", NamedTextColor.GRAY))
                .build());
            return true;
        }

        AuctionGuiListener.openGui(plugin, player, 0);
        return true;
    }
}
