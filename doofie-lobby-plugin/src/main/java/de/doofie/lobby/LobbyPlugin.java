package de.doofie.lobby;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * DOOFIE LOBBY — die Schaltzentrale des Netzwerks.
 *
 * — Jeder Spieler bekommt beim Join den MODUS-KOMPASS (Slot 5).
 *   Rechtsklick oeffnet das Auswahl-GUI mit allen 6 Spielmodi;
 *   ein Klick schickt ihn ueber Velocity auf den passenden Server.
 * — Die Lobby ist geschuetzt: kein Schaden, kein Hunger, kein
 *   Block-Abbau (ausser fuer OPs im Kreativmodus).
 */
public class LobbyPlugin extends JavaPlugin implements Listener {

    /** Anzeigename, Server-Name (velocity.toml), Icon, Beschreibung. */
    private record Modus(String titel, String server, Material icon, String beschreibung) {}

    private static final List<Modus> MODI = List.of(
        new Modus("Skyblock-Wars", "skyblock", Material.GRASS_BLOCK,
            "Himmelsinseln, Insel-Kern, Kern-Brecher!"),
        new Modus("Boss-Raids", "bossraid", Material.WITHER_SKELETON_SKULL,
            "Alle 2h ein Welt-Boss — wer macht Top-Schaden?"),
        new Modus("Kingdoms", "kingdoms", Material.GOLDEN_HELMET,
            "Koenigreiche, Claims und Kriegs-Fenster."),
        new Modus("Kopfgeldjäger 2.0", "jaeger", Material.SPYGLASS,
            "Jeder Kill macht dich wertvoller..."),
        new Modus("Zombie-Apokalypse", "zombie", Material.ZOMBIE_HEAD,
            "Nachts kommen die Horden. Blutmond alle 7 Tage!"),
        new Modus("Chaos-Events", "chaos", Material.AMETHYST_SHARD,
            "Alle 15 Minuten wuerfelt der Server."));

    private NamespacedKey kompassKey;
    private NamespacedKey serverKey;

    @Override
    public void onEnable() {
        kompassKey = new NamespacedKey(this, "modus_kompass");
        serverKey = new NamespacedKey(this, "ziel_server");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        getServer().getPluginManager().registerEvents(this, this);

        // Ewiger Tag + keine Mobs + keepInventory: Gameregeln setzen, Bestand entsorgen
        for (org.bukkit.World w : getServer().getWorlds()) {
            w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
            w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
            w.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false);
            w.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, true);
            try {
                w.setTime(6000); // High Noon
                w.setStorm(false);
            } catch (IllegalArgumentException ignoriert) {
                // 26.2-Void-Welten haben keine Welt-Uhr — dann ist eh immer Tag
            }
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e instanceof org.bukkit.entity.LivingEntity && !(e instanceof Player)) e.remove();
            }
        }
        new SchemCommand(this).register();

        // Void-Welt: falls am Spawn nichts steht, eine Plattform hinbauen
        org.bukkit.World w = getServer().getWorlds().get(0);
        org.bukkit.Location spawn = new org.bukkit.Location(w, 0.5, 101, 0.5);
        if (w.getBlockAt(0, 100, 0).getType().isAir()) {
            for (int dx = -12; dx <= 12; dx++) {
                for (int dz = -12; dz <= 12; dz++) {
                    if (dx * dx + dz * dz > 12 * 12) continue; // runde Plattform
                    w.getBlockAt(dx, 100, dz).setType(
                        (dx * dx + dz * dz > 10 * 10) ? Material.SEA_LANTERN : Material.SMOOTH_QUARTZ);
                }
            }
            getLogger().info("Spawn-Plattform in der Void-Lobby gebaut.");
        }
        w.setSpawnLocation(spawn);

        getLogger().info("Doofie-Lobby aktiv — " + MODI.size() + " Modi im Kompass, ewiger Tag, keine Mobs.");
    }

    /** Void-Rettung: Wer unter y=-20 faellt, landet wieder am Spawn. */
    @EventHandler
    public void onVoid(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo().getY() < -20) {
            Player p = event.getPlayer();
            p.teleport(p.getWorld().getSpawnLocation().clone().add(0, 1, 0));
            p.setFallDistance(0);
        }
    }

    /** Absolut nichts spawnt in der Lobby — auch keine Slimes aus Superflat-Chunks. */
    @EventHandler
    public void onSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (event.getSpawnReason() != org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason.CUSTOM) {
            event.setCancelled(true);
        }
    }

    // ────────────────────────── Kompass ──────────────────────────

    private ItemStack modusKompass() {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(kompassKey, PersistentDataType.BYTE, (byte) 1);
        meta.setItemModel(new NamespacedKey("lobby", "modus_kompass"));
        meta.displayName(Component.text("Modus-Kompass", NamedTextColor.AQUA, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Rechtsklick: Spielmodus waehlen", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        p.getInventory().setItem(4, modusKompass());
        p.sendMessage(Component.text("Willkommen im Doofie-Netzwerk! ", NamedTextColor.AQUA)
            .append(Component.text("Rechtsklick auf den Kompass fuer die Modi.", NamedTextColor.GRAY)));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()
            || !item.getItemMeta().getPersistentDataContainer().has(kompassKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        oeffneMenu(event.getPlayer());
    }

    private void oeffneMenu(Player p) {
        Inventory gui = Bukkit.createInventory(new ModusHolder(), 27,
            Component.text("Waehle deinen Modus!", NamedTextColor.DARK_AQUA, TextDecoration.BOLD));
        int[] slots = {10, 11, 12, 14, 15, 16};
        for (int i = 0; i < MODI.size(); i++) {
            Modus m = MODI.get(i);
            ItemStack icon = new ItemStack(m.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.getPersistentDataContainer().set(serverKey, PersistentDataType.STRING, m.server());
            meta.displayName(Component.text(m.titel(), NamedTextColor.AQUA, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                Component.text(m.beschreibung(), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Klick zum Beitreten!", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false)));
            icon.setItemMeta(meta);
            gui.setItem(slots[i], icon);
        }
        p.openInventory(gui);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.3f);
    }

    /** Marker, damit nur unser GUI abgefangen wird. */
    private static class ModusHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    @EventHandler
    public void onGuiKlick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof ModusHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String server = item.getItemMeta().getPersistentDataContainer()
            .get(serverKey, PersistentDataType.STRING);
        if (server == null) return;
        p.closeInventory();
        p.sendMessage(Component.text("Verbinde mit " + server + "...", NamedTextColor.AQUA));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }

    // ────────────────────────── Lobby-Schutz ──────────────────────────

    private boolean istBaumeister(Player p) {
        return p.isOp() && p.getGameMode() == GameMode.CREATIVE;
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) event.setCancelled(true);
    }

    @EventHandler
    public void onHunger(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!istBaumeister(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!istBaumeister(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (!istBaumeister(event.getPlayer())) event.setCancelled(true);
    }
}
