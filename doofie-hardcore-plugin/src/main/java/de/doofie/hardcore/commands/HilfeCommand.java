package de.doofie.hardcore.commands;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.inventory.Book;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** /hilfe — oeffnet das Doofie-Handbuch mit ALLEN Systemen. */
public class HilfeCommand implements CommandExecutor {

    private final HardcorePlugin plugin;

    public HilfeCommand(HardcorePlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }

        List<Component> pages = new ArrayList<>();
        pages.add(page("DOOFIE HARDCORE",
            "Willkommen im Handbuch!\n\nBlaettere durch die Seiten:\n\n2 Geld\n3 Tod & Freikauf\n4 Kopfgeld\n5 /ah & Shops\n6 Gilden\n7 Duelle & Wetten\n8 Events & Boerse\n9 Sonstiges\n\nViel Erfolg!"));
        pages.add(page("GELD",
            "Start: 500$\n\n/money - Guthaben\n/pay <name> <betrag>\n/sell - Verkaufsmenue: Items reinlegen, gruener Knopf!\n\nDer Verkaufswert steht unter jedem Item.\n\n/daily - taegl. Belohnung\n/quests - Tagesauftraege\n/top geld - Rangliste"));
        pages.add(page("TOD & FREIKAUF",
            "JEDER Tod = Spectator!\n\nNormaler Tod:\n/freikaufen = GRATIS zurueck ins Leben.\n\nKopfgeld-Tod (von Spieler gekillt):\n/freikaufen joker = 1x im Leben gratis\n/freikaufen zahlen = Kopfgeld +5%\n\nFreunde helfen:\n/freikaufen <name>"));
        pages.add(page("KOPFGELD",
            "/kopfgeld <name> <betrag> - setzen (min. 100$)\n/kopfgeld liste\n/auftrag <name> <betrag> - ANONYM (+20%)\n\nKill mit Kopfgeld: Killer kassiert, Kopf droppt (10% wert), Opfer gebannt!\n\n/jagd <name> - Kompass-Jagd\n/schutz - 30min Bann-Schutz (2000$)\n/gerichtsduell - kaempf um deine Freiheit (1x!)"));
        pages.add(page("HANDEL",
            "/ah - Auktionshaus:\nKlick = 1 kaufen\nShift+Klick = Stack\n/ah sell <preis> - Item einstellen\n\nKistenshop bauen:\nSchild an Kiste:\nZeile 1: [shop]\nZeile 2: Preis\n\nAndere kaufen per Rechtsklick - auch wenn du offline bist!"));
        pages.add(page("GILDEN",
            "/gilde gruenden <name> (5000$)\n/gilde einladen <name>\n/gilde beitreten\n/gilde verlassen\n/gilde kasse <betrag>\n/gilde chat <text>\n/gilde liste\n\nKRIEG:\n/gilde krieg <gilde> <einsatz>\nGegner: /gilde kriegannehmen\nErste Gilde mit 5 Kills gewinnt!"));
        pages.add(page("DUELLE & WETTEN",
            "/duell <name> <einsatz>\nGegner: /duell annehmen\n\nGewinner kriegt ALLES. Flucht (ausloggen) = Niederlage!\n\nZuschauer: 30s nach Start\n/wette <name> <betrag>\n\nGewinner-Wetten teilen sich den Verlierer-Pool."));
        pages.add(page("EVENTS & BOERSE",
            "Zufaellige Events:\nBLUTMOND - Kopfgelder x2\nGOLDRAUSCH - /sell x2\nSAEUBERUNG - jeder Kill 200$\nSTREIK - /ah zu\n\n/events - was laeuft?\n\n/boerse - Aktienmarkt, Kurse alle 10min\n/boerse kaufen <firma> <anzahl>\n/lotto kaufen - stuendl. Ziehung!"));
        pages.add(page("SONSTIGES",
            "/tpa <name> - Teleport-Anfrage (3s stillstehen!)\n/rtp [overworld|nether|end] - Zufalls-Teleport, sicherer Ort, 5min Cooldown. Nur bereiste Dimensionen, gesperrt mit Kopfgeld!\n\n/testament <name> - Erbe: nach 7 Tagen Bann erbt er dein halbes Vermoegen\n\n/top geld|kills|kopfgelder\n/sidebar - Stats-Anzeige an/aus\n\n/hilfe - dieses Buch"));

        Book book = Book.book(
            Component.text("Doofie Handbuch", NamedTextColor.GOLD),
            Component.text("Doofie"),
            pages);
        p.openBook(book);
        return true;
    }

    private Component page(String title, String body) {
        return Component.text()
            .append(Component.text(title + "\n\n", NamedTextColor.DARK_RED))
            .append(Component.text(body, NamedTextColor.BLACK))
            .build();
    }
}
