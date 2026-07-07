package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/** /sell hand — Item in der Hand verkaufen, /sell all — alles Verkaufbare im Inventar. */
public class SellCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public SellCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    private double priceOf(Material material) {
        ConfigurationSection prices = plugin.getConfig().getConfigurationSection("preise");
        if (prices == null) return 0;
        return prices.getDouble(material.name(), 0);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        String mode = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "hand";

        double earned = 0;
        int soldItems = 0;

        if (mode.equals("hand")) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            double price = priceOf(hand.getType());
            if (hand.getType().isAir() || price <= 0) {
                player.sendMessage(Component.text("Dieses Item kann nicht verkauft werden.", NamedTextColor.RED));
                return true;
            }
            earned = price * hand.getAmount();
            soldItems = hand.getAmount();
            player.getInventory().setItemInMainHand(null);
        } else if (mode.equals("all")) {
            ItemStack[] contents = player.getInventory().getStorageContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (item == null) continue;
                double price = priceOf(item.getType());
                if (price <= 0) continue;
                earned += price * item.getAmount();
                soldItems += item.getAmount();
                contents[i] = null;
            }
            player.getInventory().setStorageContents(contents);
            if (soldItems == 0) {
                player.sendMessage(Component.text("Nichts Verkaufbares im Inventar.", NamedTextColor.RED));
                return true;
            }
        } else {
            player.sendMessage(Component.text("Nutzung: /sell <hand|all>", NamedTextColor.RED));
            return true;
        }

        plugin.economy().deposit(player.getUniqueId(), earned);
        player.sendMessage(Component.text()
            .append(Component.text("Verkauft: ", NamedTextColor.GREEN))
            .append(Component.text(soldItems + " Items", NamedTextColor.WHITE))
            .append(Component.text(" fuer ", NamedTextColor.GREEN))
            .append(Component.text(HardcorePlugin.dollar(earned), NamedTextColor.GOLD))
            .build());
        return true;
    }
}
