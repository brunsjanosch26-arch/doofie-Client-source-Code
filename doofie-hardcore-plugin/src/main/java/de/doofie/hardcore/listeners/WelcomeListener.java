package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/** Erklaert neuen Spielern beim ersten Join alle Regeln und Commands. */
public class WelcomeListener implements Listener {

    private final HardcorePlugin plugin;

    public WelcomeListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onFirstJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPlayedBefore()) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            double start = plugin.getConfig().getDouble("start-guthaben", 500.0);

            boolean kopfgeld = plugin.getConfig().getBoolean("kopfgeld-system", true);
            player.sendMessage(Component.text("════════════════════════════════", NamedTextColor.DARK_RED));
            player.sendMessage(Component.text(kopfgeld
                ? "  Willkommen auf dem Bounty SMP!"
                : "  Willkommen auf dem DoofieSMP!", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
            player.sendMessage(Component.text("════════════════════════════════", NamedTextColor.DARK_RED));
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("So funktioniert der Server:", NamedTextColor.YELLOW));
            player.sendMessage(Component.text(" • Du startest mit " + HardcorePlugin.dollar(start) + " Guthaben", NamedTextColor.GRAY));
            player.sendMessage(Component.text(" • /money — dein Guthaben | /pay <spieler> <betrag> — Geld senden", NamedTextColor.GRAY));
            player.sendMessage(Component.text(" • /sell — Verkaufsmenue: Items reinlegen, bestaetigen, Geld kassieren", NamedTextColor.GRAY));
            player.sendMessage(Component.text(" • Preise siehst du im /sell-Menue und im /ah", NamedTextColor.GRAY));
            player.sendMessage(Component.text(" • /ah — Auktionshaus: kaufen per Klick (Shift+Klick = ganzer Stack)", NamedTextColor.GRAY));
            player.sendMessage(Component.text(" • /ah sell <preis> — eigenes Item im /ah anbieten", NamedTextColor.GRAY));
            player.sendMessage(Component.empty());
            if (kopfgeld) {
                player.sendMessage(Component.text("KOPFGELD — die wichtigste Regel:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text(" • /kopfgeld <spieler> <betrag> — Kopfgeld setzen (min. 100$)", NamedTextColor.GRAY));
                player.sendMessage(Component.text(" • Stirbst du OHNE Kopfgeld: normaler Respawn, nichts passiert", NamedTextColor.GRAY));
                player.sendMessage(Component.text(" • Wirst du MIT Kopfgeld von einem Spieler getoetet:", NamedTextColor.GRAY));
                player.sendMessage(Component.text("   dein Kopf droppt, der Killer kassiert und DU BIST GEBANNT!", NamedTextColor.RED));
                player.sendMessage(Component.text(" • Freikauf: /freikaufen — kostet das Kopfgeld +5%", NamedTextColor.GRAY));
                player.sendMessage(Component.text("   (Freunde koennen dich mit /freikaufen <name> retten)", NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text("PVP & GOTT-ITEMS:", NamedTextColor.RED).decorate(TextDecoration.BOLD));
                player.sendMessage(Component.text(" • Tod = ganz normaler Respawn", NamedTextColor.GRAY));
                player.sendMessage(Component.text(" • Killst du einen Spieler, droppt sein KOPF", NamedTextColor.GRAY));
                player.sendMessage(Component.text(" • Koepfe brauchst du fuer Goetterspeer & God Mace!", NamedTextColor.GRAY));
            }
            player.sendMessage(Component.text("════════════════════════════════", NamedTextColor.DARK_RED));
            player.sendMessage(Component.text("Tippe /hilfe fuer das komplette Handbuch mit ALLEN Systemen!", NamedTextColor.GOLD));
        }, 40L);
    }
}
