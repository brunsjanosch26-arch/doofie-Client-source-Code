package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.QuestManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /quests — die 3 taeglichen Auftraege anzeigen. */
public class QuestsCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public QuestsCommand(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        player.sendMessage(Component.text("── Deine Tagesauftraege ──", NamedTextColor.GOLD));
        for (QuestManager.Quest q : plugin.quests().questsOf(player.getUniqueId())) {
            NamedTextColor color = q.done ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
            String status = q.done ? "FERTIG" : q.progress + "/" + q.target;
            player.sendMessage(Component.text()
                .append(Component.text("  " + q.describe(), color))
                .append(Component.text(" [" + status + "] ", NamedTextColor.WHITE))
                .append(Component.text("-> " + HardcorePlugin.dollar(q.reward), NamedTextColor.GOLD))
                .build());
        }
        player.sendMessage(Component.text("Neue Auftraege gibt es jeden Tag!", NamedTextColor.GRAY));
        return true;
    }
}
