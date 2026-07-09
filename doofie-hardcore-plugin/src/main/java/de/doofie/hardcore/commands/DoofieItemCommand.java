package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * /doofieitem give <spieler> <set|helm|brust|hose|schuhe|doener|speer>
 * Admin-Command (Permission doofie.items, default op).
 * Die Hermes-Ruestung gibt es NUR hierueber — kein Rezept!
 */
public class DoofieItemCommand implements TabExecutor {

    private static final List<String> ITEMS =
        List.of("set", "helm", "brust", "hose", "schuhe", "doener", "speer");

    private final HardcorePlugin plugin;

    public DoofieItemCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("doofie.items")) {
            sender.sendMessage(Component.text("Dafuer hast du keine Rechte!", NamedTextColor.RED));
            return true;
        }
        if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
            sender.sendMessage(Component.text(
                "Nutzung: /doofieitem give <spieler> <set|helm|brust|hose|schuhe|doener|speer>",
                NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Component.text("Spieler nicht online.", NamedTextColor.RED));
            return true;
        }

        String id = args[2].toLowerCase(Locale.ROOT);
        List<String> parts = id.equals("set")
            ? List.of("helm", "brust", "hose", "schuhe")
            : List.of(id);

        for (String part : parts) {
            ItemStack item = plugin.customItems().byId(part);
            if (item == null) {
                sender.sendMessage(Component.text("Unbekanntes Item: " + part
                    + " — moeglich: " + String.join(", ", ITEMS), NamedTextColor.RED));
                return true;
            }
            target.getInventory().addItem(item).values()
                .forEach(rest -> target.getWorld().dropItemNaturally(target.getLocation(), rest));
        }
        sender.sendMessage(Component.text(target.getName() + " hat '" + id + "' erhalten.",
            NamedTextColor.GREEN));
        target.sendMessage(Component.text("Du hast ein legendaeres Item erhalten!", NamedTextColor.GOLD));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("doofie.items")) return List.of();
        if (args.length == 1) return List.of("give");
        if (args.length == 2) return null; // Spielernamen
        if (args.length == 3) return ITEMS.stream()
            .filter(i -> i.startsWith(args[2].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
