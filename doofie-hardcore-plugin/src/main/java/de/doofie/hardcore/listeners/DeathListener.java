package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.DuelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.Duration;
import java.util.UUID;

/**
 * Herzstueck des Kopfgeld-Systems:
 * - Duell-Tod: Gewinner bekommt den Pot, kein Bann.
 * - Kopfgeld-Tod: Killer kassiert, Kopf droppt (mit Trophaeen-Wert),
 *   Opfer wird gebannt — ausser es hat /schutz aktiv.
 * - Normaler Tod: Plugin hebt den Hardcore-Tod auf.
 */
public class DeathListener implements Listener {

    private final HardcorePlugin plugin;

    public DeathListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null && !killer.equals(victim)) {
            if (plugin.extras().inZone(victim.getLocation())) return; // Friedenszone
            plugin.stats().addKill(killer.getUniqueId());
            plugin.extras().recordKill(victim.getUniqueId(), killer.getUniqueId());
            plugin.extras().addXp(killer, 10);
            plugin.guilds().warKill(killer.getUniqueId(), victim.getUniqueId());

            // Gerichtsduell: Angeklagter vs. Killer
            var courts = plugin.duels().courtDuels();
            UUID accused = courts.containsKey(victim.getUniqueId()) ? victim.getUniqueId()
                : courts.containsKey(killer.getUniqueId()) ? killer.getUniqueId() : null;
            if (accused != null) {
                UUID opponent = courts.get(accused);
                boolean pairMatches = (victim.getUniqueId().equals(accused) && killer.getUniqueId().equals(opponent))
                    || (killer.getUniqueId().equals(accused) && victim.getUniqueId().equals(opponent));
                if (pairMatches) {
                    courts.remove(accused);
                    if (killer.getUniqueId().equals(accused)) {
                        // Angeklagter gewinnt -> frei!
                        plugin.bans().unban(accused);
                        Bukkit.broadcast(Component.text("GERICHTSDUELL: " + killer.getName()
                            + " besiegt seinen Killer und ist FREI!", NamedTextColor.GREEN));
                    } else {
                        // Angeklagter verliert -> Freikauf verdoppelt sich
                        var ban = plugin.bans().get(accused);
                        if (ban != null) ban.cost *= 2;
                        Bukkit.broadcast(Component.text("GERICHTSDUELL: " + victim.getName()
                            + " verliert — Freikauf verdoppelt auf "
                            + HardcorePlugin.dollar(ban != null ? ban.cost : 0) + "!", NamedTextColor.DARK_RED));
                    }
                    return;
                }
            }

            // Saeuberung: jeder Kill bringt 200$
            if (plugin.events().isActive(de.doofie.hardcore.managers.EventManager.EventType.SAEUBERUNG)) {
                plugin.economy().deposit(killer.getUniqueId(), 200);
                killer.sendMessage(Component.text("SAEUBERUNG: +200$ fuer den Kill!", NamedTextColor.DARK_RED));
            }
        }

        // ── Duell-Tod: Pot an den Gewinner, kein Bann ──
        DuelManager.ActiveDuel duel = plugin.duels().duelOf(victim.getUniqueId());
        if (duel != null && killer != null
                && (killer.getUniqueId().equals(duel.a()) || killer.getUniqueId().equals(duel.b()))) {
            plugin.duels().endDuel(duel);
            plugin.economy().deposit(killer.getUniqueId(), duel.pot());
            payoutBets(killer.getUniqueId());
            Bukkit.broadcast(Component.text()
                .append(Component.text("DUELL VORBEI! ", NamedTextColor.RED))
                .append(Component.text(killer.getName(), NamedTextColor.GOLD))
                .append(Component.text(" besiegt " + victim.getName() + " und gewinnt ", NamedTextColor.GRAY))
                .append(Component.text(HardcorePlugin.dollar(duel.pot()), NamedTextColor.GOLD))
                .build());
            return;
        }

        // SMP-Modus (kopfgeld-system: false): kein Kopfgeld-Zeug, aber bei
        // JEDEM Spieler-Kill droppt der Kopf des Opfers (fuers Gott-Item-Crafting)
        if (!plugin.getConfig().getBoolean("kopfgeld-system", true)) {
            if (killer != null && !killer.equals(victim)) {
                ItemStack kopf = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta kopfMeta = (SkullMeta) kopf.getItemMeta();
                kopfMeta.setOwningPlayer(victim);
                kopfMeta.displayName(Component.text("Kopf von " + victim.getName(), NamedTextColor.GOLD));
                kopf.setItemMeta(kopfMeta);
                victim.getWorld().dropItemNaturally(victim.getLocation(), kopf);
                killer.sendMessage(Component.text("Der Kopf von " + victim.getName()
                    + " ist gedroppt — Crafting-Material!", NamedTextColor.GOLD));
            }
            return;
        }

        double bounty = plugin.bounties().total(victim.getUniqueId());

        if (bounty > 0 && killer != null && !killer.equals(victim)) {
            // ── Kopfgeld-Kill (beim Blutmond zaehlt es doppelt) ──
            plugin.bounties().claim(victim.getUniqueId());
            double payout = bounty * plugin.events().bountyMultiplier();

            // Kopfgeld-Splitting: alle Angreifer der letzten 10s teilen sich die Beute
            var assists = de.doofie.hardcore.listeners.GameListener.ASSISTS.remove(victim.getUniqueId());
            java.util.Set<UUID> helpers = new java.util.HashSet<>();
            helpers.add(killer.getUniqueId());
            if (assists != null) {
                long now = System.currentTimeMillis();
                assists.forEach((a, t) -> { if (now - t < 10_000 && !a.equals(victim.getUniqueId())) helpers.add(a); });
            }
            double share = payout / helpers.size();
            for (UUID h : helpers) {
                plugin.economy().deposit(h, share);
                Player hp = Bukkit.getPlayer(h);
                if (hp != null && helpers.size() > 1) hp.sendMessage(Component.text(
                    "Kopfgeld geteilt (" + helpers.size() + " Jaeger): +" + HardcorePlugin.dollar(share), NamedTextColor.GOLD));
            }
            if (payout > bounty) {
                killer.sendMessage(Component.text("BLUTMOND: Kopfgeld verdoppelt!", NamedTextColor.DARK_RED));
            }

            boolean protectedVictim = plugin.protection().isProtected(victim.getUniqueId());
            if (!protectedVictim) {
                plugin.bans().ban(victim.getUniqueId(), bounty, killer.getUniqueId());
            }

            // Kopf des Opfers droppen — als Trophaee mit eingebautem Wert
            double kopfBonus = plugin.getConfig().getDouble("kopf-bonus", 0.10);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(victim);
            meta.displayName(Component.text("Kopf von " + victim.getName(), NamedTextColor.GOLD));
            meta.getPersistentDataContainer().set(plugin.headKey(), PersistentDataType.DOUBLE, bounty * kopfBonus);
            head.setItemMeta(meta);
            victim.getWorld().dropItemNaturally(victim.getLocation(), head);

            // Grosse Titel-Einblendung fuer alle
            Title title = Title.title(
                Component.text("KOPFGELD KASSIERT!", NamedTextColor.DARK_RED),
                Component.text(killer.getName() + " erledigt " + victim.getName() + " fuer " + HardcorePlugin.dollar(bounty),
                    NamedTextColor.GOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800)));
            Bukkit.getOnlinePlayers().forEach(p -> p.showTitle(title));

            if (protectedVictim) {
                Bukkit.broadcast(Component.text(
                    victim.getName() + " hatte Bounty-Schutz und wird NICHT gebannt!", NamedTextColor.AQUA));
            } else {
                double unbanCost = plugin.bans().unbanCost(victim.getUniqueId());
                Bukkit.broadcast(Component.text(
                    victim.getName() + " ist gebannt! Freikauf: " + HardcorePlugin.dollar(unbanCost), NamedTextColor.RED));
            }
        }
    }

    /** Wetten proportional ausschuetten: Gewinner teilen sich den Verlierer-Pool. */
    private void payoutBets(UUID winner) {
        var bets = plugin.duels().takeBets();
        if (bets.isEmpty()) return;
        double winPool = bets.stream().filter(b -> b.on().equals(winner)).mapToDouble(b -> b.amount()).sum();
        double losePool = bets.stream().filter(b -> !b.on().equals(winner)).mapToDouble(b -> b.amount()).sum();
        for (var bet : bets) {
            if (!bet.on().equals(winner)) continue;
            double share = winPool > 0 ? bet.amount() / winPool : 0;
            double prize = bet.amount() + losePool * share;
            plugin.economy().deposit(bet.bettor(), prize);
            Player p = Bukkit.getPlayer(bet.bettor());
            if (p != null) p.sendMessage(Component.text("WETTE GEWONNEN: +" + HardcorePlugin.dollar(prize), NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        // SMP-Modus: ganz normaler Survival-Respawn, kein Spectator-Ritual
        if (!plugin.getConfig().getBoolean("hardcore-bann", true)) return;
        // Nach dem Respawn: Gebannte in den Spectator, alle anderen ueberleben
        // den Hardcore-Tod (Bann gibt es NUR ueber Kopfgeld).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            player.setGameMode(GameMode.SPECTATOR);
            if (plugin.bans().isBanned(player.getUniqueId())) {
                double cost = plugin.bans().unbanCost(player.getUniqueId());
                player.sendMessage(Component.text(
                    "Du wurdest per Kopfgeld gebannt! Freikauf: " + HardcorePlugin.dollar(cost)
                    + " — /freikaufen zeigt dir deine Optionen (Joker oder zahlen).",
                    NamedTextColor.RED));
            } else {
                plugin.bans().markDead(player.getUniqueId());
                player.sendMessage(Component.text(
                    "Du bist tot! Tippe /freikaufen und du bist GRATIS wieder im Spiel.",
                    NamedTextColor.YELLOW));
            }
        }, 1L);
    }
}
