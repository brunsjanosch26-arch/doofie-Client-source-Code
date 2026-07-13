package de.doofie.jaeger;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Dynamische Kopfgelder, Geldkonten und die drei Jagd-Items.
 */
public class JagdManager implements Listener, TabExecutor {

    private static final double KILL_AUFSCHLAG = 100.0;

    private final JaegerPlugin plugin;
    private final NamespacedKey itemKey;
    private final File file;
    /** Spieler -> eigenes Kopfgeld */
    private final Map<UUID, Double> kopfgeld = new HashMap<>();
    /** Spieler -> Kontostand */
    private final Map<UUID, Double> konto = new HashMap<>();
    /** Tarnumhang: Ende der Tarnung (ms) */
    private final Map<UUID, Long> getarnt = new HashMap<>();
    /** Tarnumhang-Cooldowns */
    private final Map<UUID, Long> tarnCooldown = new HashMap<>();
    /** Fernrohr-Cooldowns */
    private final Map<UUID, Long> fernrohrCooldown = new HashMap<>();
    /** Blutsiegel: Jaeger -> (Ziel, Ende) */
    private final Map<UUID, Map.Entry<UUID, Long>> siegel = new HashMap<>();

    public JagdManager(JaegerPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
        this.file = new File(plugin.getDataFolder(), "jagd.yml");
        load();
    }

    public void start() {
        // Tarnumhang-Rezept: Umhang aus Phantomhaeuten + Faeden
        var tarn = new org.bukkit.inventory.ShapedRecipe(new NamespacedKey(plugin, "tarnumhang"), tarnumhang());
        tarn.shape("FPF", "PEP", "FPF");
        tarn.setIngredient('F', Material.STRING);
        tarn.setIngredient('P', Material.PHANTOM_MEMBRANE);
        tarn.setIngredient('E', Material.ENDER_EYE);
        Bukkit.addRecipe(tarn);

        // Blutsiegel-Rezept
        var blut = new org.bukkit.inventory.ShapedRecipe(new NamespacedKey(plugin, "blutsiegel"), blutsiegel());
        blut.shape("RRR", "RNR", "RRR");
        blut.setIngredient('R', Material.REDSTONE_BLOCK);
        blut.setIngredient('N', Material.NAME_TAG);
        Bukkit.addRecipe(blut);
    }

    // ────────────────────────── Persistenz ──────────────────────────

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (yml.isConfigurationSection("kopfgeld")) {
            for (String k : yml.getConfigurationSection("kopfgeld").getKeys(false)) {
                kopfgeld.put(UUID.fromString(k), yml.getDouble("kopfgeld." + k));
            }
        }
        if (yml.isConfigurationSection("konto")) {
            for (String k : yml.getConfigurationSection("konto").getKeys(false)) {
                konto.put(UUID.fromString(k), yml.getDouble("konto." + k));
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        kopfgeld.forEach((k, v) -> yml.set("kopfgeld." + k, v));
        konto.forEach((k, v) -> yml.set("konto." + k, v));
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("jagd.yml: " + ex.getMessage());
        }
    }

    // ────────────────────────── Kopfgeld-Logik ──────────────────────────

    /** Ist ziel das aktuell versiegelte Jagdziel von jaeger? */
    public boolean istJagdziel(UUID jaeger, UUID ziel) {
        Map.Entry<UUID, Long> s = siegel.get(jaeger);
        return s != null && s.getKey().equals(ziel) && s.getValue() > System.currentTimeMillis();
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player opfer = event.getEntity();
        Player killer = opfer.getKiller();
        if (killer == null || killer.equals(opfer)) return;

        double beute = kopfgeld.getOrDefault(opfer.getUniqueId(), 0.0);

        // Blutsiegel eines ANDEREN Jaegers auf dem Opfer? Dann geht die Beute an ihn.
        for (Map.Entry<UUID, Map.Entry<UUID, Long>> e : siegel.entrySet()) {
            if (!e.getValue().getKey().equals(opfer.getUniqueId())) continue;
            if (e.getValue().getValue() < System.currentTimeMillis()) continue;
            if (!e.getKey().equals(killer.getUniqueId())) {
                killer.sendMessage(Component.text("Das Kopfgeld ist BLUTVERSIEGELT — ein anderer Jaeger kassiert!",
                    NamedTextColor.RED));
                Player siegler = Bukkit.getPlayer(e.getKey());
                if (siegler != null && beute > 0) {
                    konto.merge(siegler.getUniqueId(), beute, Double::sum);
                    siegler.sendMessage(Component.text("Dein Blutsiegel zahlt aus: +" + Math.round(beute) + "$ ("
                        + opfer.getName() + " wurde erlegt).", NamedTextColor.GOLD));
                }
                beute = 0;
            }
            siegel.remove(e.getKey());
            break;
        }

        if (beute > 0) {
            konto.merge(killer.getUniqueId(), beute, Double::sum);
            killer.sendMessage(Component.text("💰 Kopfgeld kassiert: +" + Math.round(beute) + "$!",
                NamedTextColor.GOLD));
        }
        kopfgeld.put(opfer.getUniqueId(), 0.0);

        // Der Killer wird selbst wertvoller
        double neu = kopfgeld.merge(killer.getUniqueId(), KILL_AUFSCHLAG, Double::sum);
        Bukkit.broadcast(Component.text("🎯 " + killer.getName() + " hat " + opfer.getName()
            + " erlegt — sein eigenes Kopfgeld steigt auf " + Math.round(neu) + "$!", NamedTextColor.YELLOW));
        save();
    }

    // ────────────────────────── Items ──────────────────────────

    private ItemStack tagged(Material mat, String id, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("jaeger", id));
        meta.displayName(Component.text(name, color, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream()
            .map(l -> Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
            .toList());
        item.setItemMeta(meta);
        return item;
    }

    private String idOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public ItemStack fernrohr() {
        return tagged(Material.SPYGLASS, "jaeger_fernrohr", "Jäger-Fernrohr", NamedTextColor.YELLOW,
            List.of("Rechtsklick: peilt den wertvollsten",
                "Spieler an — Name, Distanz, Richtung",
                "(10s Cooldown). Getarnte bleiben unsichtbar."));
    }

    public ItemStack tarnumhang() {
        return tagged(Material.PHANTOM_MEMBRANE, "tarnumhang", "Tarnumhang", NamedTextColor.DARK_GRAY,
            List.of("Rechtsklick: 5 Minuten unsichtbar",
                "fuer das Jaeger-Fernrohr (15 Min Cooldown)."));
    }

    public ItemStack blutsiegel() {
        ItemStack item = tagged(Material.RED_DYE, "blutsiegel", "Blutsiegel", NamedTextColor.DARK_RED,
            List.of("Rechtsklick AUF einen Spieler:",
                "10 Minuten lang kassierst nur DU",
                "sein Kopfgeld (wird verbraucht)."));
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack byId(String id) {
        return switch (id) {
            case "jaeger_fernrohr" -> fernrohr();
            case "tarnumhang" -> tarnumhang();
            case "blutsiegel" -> blutsiegel();
            default -> null;
        };
    }

    /** Fernrohr + Tarnumhang per Rechtsklick in die Luft. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = idOf(event.getItem());
        if (id == null) return;
        Player p = event.getPlayer();
        long jetzt = System.currentTimeMillis();

        if (id.equals("jaeger_fernrohr")) {
            event.setCancelled(true);
            Long cd = fernrohrCooldown.get(p.getUniqueId());
            if (cd != null && cd > jetzt) return;
            fernrohrCooldown.put(p.getUniqueId(), jetzt + 10_000);

            Player bestes = null;
            double bestesGeld = 0;
            for (Player ziel : Bukkit.getOnlinePlayers()) {
                if (ziel.equals(p)) continue;
                Long tarnEnde = getarnt.get(ziel.getUniqueId());
                if (tarnEnde != null && tarnEnde > jetzt) continue; // getarnt
                double geld = kopfgeld.getOrDefault(ziel.getUniqueId(), 0.0);
                if (geld > bestesGeld) { bestesGeld = geld; bestes = ziel; }
            }
            if (bestes == null) {
                p.sendMessage(Component.text("Kein lohnendes Ziel in Sicht.", NamedTextColor.GRAY));
                return;
            }
            String richtung = "irgendwo";
            double distanz = -1;
            if (bestes.getWorld().equals(p.getWorld())) {
                distanz = bestes.getLocation().distance(p.getLocation());
                Vector d = bestes.getLocation().toVector().subtract(p.getLocation().toVector());
                double winkel = Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
                double relativ = ((winkel - p.getLocation().getYaw()) % 360 + 540) % 360 - 180;
                richtung = Math.abs(relativ) < 45 ? "VOR dir"
                    : Math.abs(relativ) > 135 ? "HINTER dir"
                    : relativ > 0 ? "RECHTS von dir" : "LINKS von dir";
            }
            p.playSound(p.getLocation(), Sound.ITEM_SPYGLASS_USE, 1f, 1f);
            p.sendMessage(Component.text("🎯 " + bestes.getName() + " (" + Math.round(bestesGeld) + "$) — "
                + (distanz >= 0 ? Math.round(distanz) + " Bloecke, " + richtung : "andere Dimension"),
                NamedTextColor.YELLOW));
        }

        if (id.equals("tarnumhang")) {
            event.setCancelled(true);
            Long cd = tarnCooldown.get(p.getUniqueId());
            if (cd != null && cd > jetzt) {
                p.sendMessage(Component.text("Der Umhang braucht noch " + ((cd - jetzt) / 60_000 + 1)
                    + " Minuten.", NamedTextColor.GRAY));
                return;
            }
            tarnCooldown.put(p.getUniqueId(), jetzt + 15 * 60_000);
            getarnt.put(p.getUniqueId(), jetzt + 5 * 60_000);
            p.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 0.7f);
            p.sendMessage(Component.text("Du bist 5 Minuten unsichtbar fuer Fernrohre.", NamedTextColor.DARK_GRAY));
        }
    }

    /** Blutsiegel per Rechtsklick auf einen Spieler. */
    @EventHandler
    public void onSiegel(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player ziel)) return;
        Player p = event.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!"blutsiegel".equals(idOf(hand))) return;
        event.setCancelled(true);
        hand.subtract();
        siegel.put(p.getUniqueId(), Map.entry(ziel.getUniqueId(), System.currentTimeMillis() + 10 * 60_000));
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 0.6f);
        p.sendMessage(Component.text("Blutsiegel auf " + ziel.getName()
            + " — 10 Minuten kassierst nur DU sein Kopfgeld!", NamedTextColor.DARK_RED));
        ziel.sendMessage(Component.text("Ein Blutsiegel wurde auf dich gelegt — jemand jagt dich PERSOENLICH...",
            NamedTextColor.DARK_RED));
    }

    // ────────────────────────── Befehle ──────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        switch (cmd.getName()) {
            case "kopfgeld" -> {
                if (args.length > 0) {
                    var ziel = Bukkit.getOfflinePlayerIfCached(args[0]);
                    if (ziel == null) {
                        sender.sendMessage(Component.text("Spieler unbekannt.", NamedTextColor.RED));
                        return true;
                    }
                    sender.sendMessage(Component.text("🎯 Kopfgeld auf " + ziel.getName() + ": "
                        + Math.round(kopfgeld.getOrDefault(ziel.getUniqueId(), 0.0)) + "$", NamedTextColor.YELLOW));
                } else {
                    sender.sendMessage(Component.text("🎯 Top-Kopfgelder:", NamedTextColor.YELLOW));
                    kopfgeld.entrySet().stream()
                        .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).limit(5)
                        .forEach(e -> sender.sendMessage(Component.text(" — "
                            + Bukkit.getOfflinePlayer(e.getKey()).getName() + ": "
                            + Math.round(e.getValue()) + "$", NamedTextColor.GRAY)));
                }
            }
            case "geld" -> {
                if (sender instanceof Player p) {
                    sender.sendMessage(Component.text("💰 Kontostand: "
                        + Math.round(konto.getOrDefault(p.getUniqueId(), 0.0)) + "$", NamedTextColor.GOLD));
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equals("kopfgeld") && args.length == 1) return null; // Spielernamen
        return List.of();
    }
}
