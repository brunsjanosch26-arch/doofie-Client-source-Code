package de.doofie.lifesteal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Alle Lifesteal-Befehle:
 *   /herzen [spieler]        — Herzen anzeigen
 *   /auszahlen [anzahl]      — eigene Herzen als Herz-Items auszahlen (min. 1 bleibt)
 *   /revive <spieler>        — eliminierten Spieler zurueckholen (Revive-Beacon in der Hand)
 *   /eliminiert              — Liste aller Eliminierten
 *   /lifestealitem give ...  — Admin: Custom-Items vergeben
 */
public class LifestealCommands implements CommandExecutor {

    private final LifestealPlugin plugin;

    public LifestealCommands(LifestealPlugin plugin) {
        this.plugin = plugin;
    }

    public void register() {
        for (String cmd : new String[]{"herzen", "auszahlen", "revive", "eliminiert", "lifestealitem"}) {
            plugin.getCommand(cmd).setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        HeartManager hearts = plugin.getHeartManager();
        switch (command.getName()) {

            case "herzen" -> {
                OfflinePlayer ziel;
                if (args.length > 0) {
                    ziel = Bukkit.getOfflinePlayerIfCached(args[0]);
                    if (ziel == null) {
                        sender.sendMessage(Component.text("Spieler unbekannt: " + args[0], NamedTextColor.RED));
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    ziel = p;
                } else {
                    sender.sendMessage(Component.text("Nutzung: /herzen <spieler>", NamedTextColor.RED));
                    return true;
                }
                String status = hearts.istEliminiert(ziel.getUniqueId()) ? " (ELIMINIERT)" : "";
                sender.sendMessage(Component.text("❤ " + ziel.getName() + ": "
                    + hearts.getHerzen(ziel.getUniqueId()) + "/" + HeartManager.MAX_HERZEN
                    + " Herzen" + status, NamedTextColor.RED));
                return true;
            }

            case "auszahlen" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Nur fuer Spieler.", NamedTextColor.RED));
                    return true;
                }
                int anzahl = 1;
                if (args.length > 0) {
                    try {
                        anzahl = Math.max(1, Integer.parseInt(args[0]));
                    } catch (NumberFormatException ex) {
                        p.sendMessage(Component.text("Nutzung: /auszahlen [anzahl]", NamedTextColor.RED));
                        return true;
                    }
                }
                int aktuell = hearts.getHerzen(p.getUniqueId());
                if (aktuell - anzahl < 1) {
                    p.sendMessage(Component.text("Mindestens 1 Herz muss dir bleiben! (Du hast "
                        + aktuell + ")", NamedTextColor.RED));
                    return true;
                }
                hearts.setHerzen(p.getUniqueId(), aktuell - anzahl);
                ItemStack herzItem = plugin.getItems().herz();
                herzItem.setAmount(anzahl);
                p.getInventory().addItem(herzItem).values()
                    .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
                p.sendMessage(Component.text("❤ " + anzahl + " Herz(en) ausgezahlt — verschenke oder handle sie!",
                    NamedTextColor.GREEN));
                return true;
            }

            case "revive" -> {
                if (!(sender instanceof Player p)) {
                    sender.sendMessage(Component.text("Nur fuer Spieler.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 1) {
                    p.sendMessage(Component.text("Nutzung: /revive <spieler>", NamedTextColor.RED));
                    return true;
                }
                OfflinePlayer ziel = Bukkit.getOfflinePlayerIfCached(args[0]);
                if (ziel == null || !hearts.istEliminiert(ziel.getUniqueId())) {
                    p.sendMessage(Component.text(args[0] + " ist nicht eliminiert.", NamedTextColor.RED));
                    return true;
                }
                boolean admin = p.hasPermission("doofie.lifesteal.admin");
                ItemStack hand = p.getInventory().getItemInMainHand();
                boolean beacon = LifestealItems.REVIVE_BEACON.equals(plugin.getItems().idOf(hand));
                if (!admin && !beacon) {
                    p.sendMessage(Component.text("Du brauchst einen Revive-Beacon in der Hand!", NamedTextColor.RED));
                    return true;
                }
                if (beacon) hand.subtract();
                hearts.revive(ziel);
                return true;
            }

            case "eliminiert" -> {
                var elim = hearts.getEliminierte();
                if (elim.isEmpty()) {
                    sender.sendMessage(Component.text("Niemand ist eliminiert — noch.", NamedTextColor.GREEN));
                    return true;
                }
                sender.sendMessage(Component.text("☠ Eliminierte Spieler:", NamedTextColor.DARK_RED));
                for (UUID u : elim) {
                    sender.sendMessage(Component.text(" — " + Bukkit.getOfflinePlayer(u).getName(),
                        NamedTextColor.GRAY));
                }
                return true;
            }

            case "lifestealitem" -> {
                if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
                    sender.sendMessage(Component.text(
                        "Nutzung: /lifestealitem give <spieler> <herz|herz_fragment|revive_beacon|lifesteal_schwert> [anzahl]",
                        NamedTextColor.RED));
                    return true;
                }
                Player ziel = Bukkit.getPlayerExact(args[1]);
                if (ziel == null) {
                    sender.sendMessage(Component.text("Spieler nicht online: " + args[1], NamedTextColor.RED));
                    return true;
                }
                ItemStack item = plugin.getItems().byId(args[2].toLowerCase());
                if (item == null) {
                    sender.sendMessage(Component.text("Unbekanntes Item: " + args[2], NamedTextColor.RED));
                    return true;
                }
                if (args.length > 3) {
                    try {
                        item.setAmount(Math.max(1, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) { }
                }
                ziel.getInventory().addItem(item).values()
                    .forEach(rest -> ziel.getWorld().dropItemNaturally(ziel.getLocation(), rest));
                sender.sendMessage(Component.text("Item vergeben an " + ziel.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
        }
        return true;
    }
}
