package de.doofie.pvp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

/**
 * DOOFIE PVP-DUELL — 1v1-Arena-Server (max. 2 Spieler).
 *
 * Kit kommt aus der config.yml (kit: sword | uhc):
 *   SWORD: Diamant-Schwert + volle Diamant-Ruestung + Steaks
 *   UHC:   klassisches UHC-Endkit (Eisen-Ruestung, Schwert, Bogen,
 *          Gapples, Lava/Wasser-Eimer, Cobble) + KEINE natuerliche Regen
 *
 * Ablauf: Sobald 2 Spieler online sind, laeuft ein 5s-Countdown,
 * beide werden 40 Bloecke auseinander teleportiert und bekommen ihr Kit.
 * Tod oder Disconnect = der andere gewinnt; danach Auto-Reset und
 * (wenn beide bleiben) direkt die naechste Runde. /lobby fuehrt zurueck.
 */
public class PvpPlugin extends JavaPlugin implements Listener {

    private enum Phase { WARTEN, COUNTDOWN, KAMPF }

    private Phase phase = Phase.WARTEN;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        new LobbyCommand(this).register();

        World w = welt();
        w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
        try {
            w.setTime(6000);
        } catch (IllegalArgumentException ignoriert) { }
        // UHC kaempft ohne natuerliche Regeneration
        w.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, !kit().equals("uhc"));

        getLogger().info("PvP-Duell aktiv — Kit: " + kit().toUpperCase());
    }

    private String kit() {
        return getConfig().getString("kit", "sword").toLowerCase();
    }

    private World welt() {
        return Bukkit.getWorlds().get(0);
    }

    private Location spawn() {
        World w = welt();
        return new Location(w, 0.5, w.getHighestBlockYAt(0, 0) + 1, 0.5);
    }

    // ────────────────────────── Ablauf ──────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        reset(p);
        p.teleport(spawn());
        p.sendMessage(Component.text("⚔ 1v1-DUELL (" + kit().toUpperCase() + "-Kit) — sobald dein Gegner da ist, geht es los!",
            NamedTextColor.GOLD));
        pruefeStart();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (phase == Phase.KAMPF) {
            // Flucht = Niederlage
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.equals(event.getPlayer())) sieg(p, event.getPlayer().getName() + " ist gefluechtet");
            }
        }
        phase = Phase.WARTEN;
    }

    private void pruefeStart() {
        if (phase != Phase.WARTEN) return;
        if (Bukkit.getOnlinePlayers().size() < 2) return;
        phase = Phase.COUNTDOWN;
        final int[] rest = {5};
        Bukkit.getScheduler().runTaskTimer(this, task -> {
            if (Bukkit.getOnlinePlayers().size() < 2) {
                task.cancel();
                phase = Phase.WARTEN;
                return;
            }
            if (rest[0] > 0) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.showTitle(Title.title(
                        Component.text(rest[0], NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text("Mach dich bereit!", NamedTextColor.GRAY),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
                }
                rest[0]--;
            } else {
                task.cancel();
                starteKampf();
            }
        }, 0L, 20L);
    }

    private void starteKampf() {
        phase = Phase.KAMPF;
        Player[] spieler = Bukkit.getOnlinePlayers().toArray(new Player[0]);
        Location mitte = spawn();
        for (int i = 0; i < 2 && i < spieler.length; i++) {
            Player p = spieler[i];
            reset(p);
            int dx = i == 0 ? -20 : 20;
            Location ort = mitte.clone().add(dx, 0, 0);
            ort.setY(welt().getHighestBlockYAt(ort) + 1);
            ort.setYaw(i == 0 ? -90 : 90); // einander zugewandt
            p.teleport(ort);
            gibKit(p);
            p.showTitle(Title.title(
                Component.text("KAMPF!", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text("Moege der Bessere gewinnen.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(500))));
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.4f);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().clear();
        Player tot = event.getEntity();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (tot.isOnline()) tot.spigot().respawn();
        }, 1L);
        if (phase != Phase.KAMPF) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.equals(tot)) sieg(p, tot.getName() + " wurde erledigt");
        }
    }

    /** Kein Schaden ausserhalb des Kampfs (Wartephase/Countdown). */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (phase != Phase.KAMPF && event.getEntity() instanceof Player) {
            event.setCancelled(true);
        }
    }

    private void sieg(Player gewinner, String grund) {
        phase = Phase.WARTEN;
        Bukkit.broadcast(Component.text("🏆 " + gewinner.getName() + " GEWINNT das Duell! (" + grund + ")",
            NamedTextColor.GOLD, TextDecoration.BOLD));
        gewinner.playSound(gewinner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                reset(p);
                p.teleport(spawn());
            }
            pruefeStart(); // Rematch, wenn beide geblieben sind
        }, 100L);
    }

    private void reset(Player p) {
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        p.setHealth(20.0);
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setFireTicks(0);
        p.getActivePotionEffects().forEach(e -> p.removePotionEffect(e.getType()));
    }

    // ────────────────────────── Kits ──────────────────────────

    private void gibKit(Player p) {
        var inv = p.getInventory();
        if (kit().equals("uhc")) {
            inv.setHelmet(new ItemStack(Material.IRON_HELMET));
            inv.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            inv.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
            inv.setBoots(new ItemStack(Material.IRON_BOOTS));
            inv.addItem(
                new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.BOW),
                new ItemStack(Material.IRON_AXE),
                new ItemStack(Material.GOLDEN_APPLE, 8),
                new ItemStack(Material.WATER_BUCKET),
                new ItemStack(Material.LAVA_BUCKET),
                new ItemStack(Material.COBBLESTONE, 64),
                new ItemStack(Material.OAK_PLANKS, 32),
                new ItemStack(Material.ARROW, 24),
                new ItemStack(Material.COOKED_BEEF, 16));
        } else { // sword
            ItemStack schwert = new ItemStack(Material.DIAMOND_SWORD);
            var meta = schwert.getItemMeta();
            meta.addEnchant(Enchantment.SHARPNESS, 1, true);
            meta.setUnbreakable(true);
            schwert.setItemMeta(meta);
            inv.setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            inv.setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            inv.setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
            inv.setBoots(new ItemStack(Material.DIAMOND_BOOTS));
            inv.addItem(schwert, new ItemStack(Material.COOKED_BEEF, 32));
        }
    }
}
