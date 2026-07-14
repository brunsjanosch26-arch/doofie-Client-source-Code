package de.doofie.skyblock;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Inseln, Insel-Level und die drei Custom-Items.
 * Inseln liegen im Grid: Insel n bei (n*512, 120, 0) in der Hauptwelt.
 */
public class IslandManager implements Listener, TabExecutor {

    private static final int ABSTAND = 256;
    private static final int Y = 120;
    private static final int RADIUS = 100;

    private final SkyblockPlugin plugin;
    private final NamespacedKey itemKey;
    private final File file;
    /** Spieler -> Insel-Index */
    private final Map<UUID, Integer> inseln = new HashMap<>();
    /** Spieler -> Insel-Level */
    private final Map<UUID, Integer> level = new HashMap<>();
    /** Void-Angel-Cooldowns */
    private final Map<UUID, Long> angelCooldown = new HashMap<>();
    /** Kompass-Cooldowns */
    private final Map<UUID, Long> kompassCooldown = new HashMap<>();

    public IslandManager(SkyblockPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
        this.file = new File(plugin.getDataFolder(), "islands.yml");
        load();
    }

    public void start() {
        // Rezept: Kern-Brecher — Netherite-Spitzhacke + 4 Diamantbloecke + 4 Obsidian
        var brecher = new org.bukkit.inventory.ShapedRecipe(
            new NamespacedKey(plugin, "kern_brecher"), kernBrecher());
        brecher.shape("ODO", "DPD", "ODO");
        brecher.setIngredient('O', Material.OBSIDIAN);
        brecher.setIngredient('D', Material.DIAMOND_BLOCK);
        brecher.setIngredient('P', Material.NETHERITE_PICKAXE);
        Bukkit.addRecipe(brecher);
    }

    // ────────────────────────── Persistenz ──────────────────────────

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (yml.isConfigurationSection("inseln")) {
            for (String k : yml.getConfigurationSection("inseln").getKeys(false)) {
                inseln.put(UUID.fromString(k), yml.getInt("inseln." + k));
            }
        }
        if (yml.isConfigurationSection("level")) {
            for (String k : yml.getConfigurationSection("level").getKeys(false)) {
                level.put(UUID.fromString(k), yml.getInt("level." + k));
            }
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        inseln.forEach((k, v) -> yml.set("inseln." + k, v));
        level.forEach((k, v) -> yml.set("level." + k, v));
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("islands.yml: " + ex.getMessage());
        }
    }

    // ────────────────────────── Inseln ──────────────────────────

    private World welt() {
        return Bukkit.getWorlds().get(0);
    }

    private Location inselMitte(int index) {
        return new Location(welt(), index * ABSTAND + 0.5, Y + 1, 0.5);
    }

    /**
     * Baut die Start-Insel: 9x9-Plattform mit Kern und Kiste — plus
     * 4 NEBEN-INSELN drumherum (Baum, Erze, Sand, Schatz), damit die
     * Void-Welt nach Archipel aussieht und es was zu erbruecken gibt.
     */
    private void baueInsel(int index) {
        Location mitte = inselMitte(index);
        World w = mitte.getWorld();
        int cx = mitte.getBlockX(), cz = mitte.getBlockZ();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                w.getBlockAt(cx + dx, Y - 1, cz + dz).setType(Material.DIRT);
                w.getBlockAt(cx + dx, Y, cz + dz).setType(Material.GRASS_BLOCK);
            }
        }
        // Insel-Kern in der Mitte
        w.getBlockAt(cx, Y + 1, cz).setType(Material.LODESTONE);
        // Starterkiste — genug Bloecke, um die erste Nachbar-Insel zu erreichen
        Block kiste = w.getBlockAt(cx + 2, Y + 1, cz + 2);
        kiste.setType(Material.CHEST);
        if (kiste.getState() instanceof org.bukkit.block.Chest c) {
            c.getInventory().addItem(
                new ItemStack(Material.COBBLESTONE, 64),
                new ItemStack(Material.COBBLESTONE, 64),
                new ItemStack(Material.DIRT, 32),
                new ItemStack(Material.OAK_SAPLING, 3),
                new ItemStack(Material.ICE, 2),
                new ItemStack(Material.LAVA_BUCKET),
                new ItemStack(Material.WATER_BUCKET),
                new ItemStack(Material.BREAD, 12),
                inselKompass(),
                voidAngel());
        }
        // Setzling-Ecke
        w.getBlockAt(cx - 3, Y + 1, cz - 3).setType(Material.OAK_SAPLING);

        // 4 Neben-Inseln in zufaelligen Richtungen (35-55 Bloecke entfernt)
        java.util.Random zufall = new java.util.Random(index * 7919L);
        double startwinkel = zufall.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 4; i++) {
            double winkel = startwinkel + i * Math.PI / 2 + zufall.nextDouble() * 0.6;
            int abstand = 35 + zufall.nextInt(21);
            int nx = cx + (int) (Math.cos(winkel) * abstand);
            int nz = cz + (int) (Math.sin(winkel) * abstand);
            int ny = Y + zufall.nextInt(9) - 4;
            baueNebeninsel(w, nx, ny, nz, i, zufall);
        }
    }

    /** Kleine Neben-Insel: 0=Baum, 1=Erze, 2=Sand, 3=Schatz. */
    private void baueNebeninsel(World w, int cx, int cy, int cz, int typ, java.util.Random zufall) {
        // 5x5-Plattform mit abgerundeten Ecken
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                Material boden = switch (typ) {
                    case 1 -> Material.STONE;
                    case 2 -> Material.SAND;
                    default -> Material.DIRT;
                };
                w.getBlockAt(cx + dx, cy - 1, cz + dz).setType(boden);
                if (typ == 0 || typ == 3) {
                    w.getBlockAt(cx + dx, cy, cz + dz).setType(Material.GRASS_BLOCK);
                } else {
                    w.getBlockAt(cx + dx, cy, cz + dz).setType(boden);
                }
            }
        }
        switch (typ) {
            case 0 -> { // Baum-Insel: ausgewachsene Eiche
                Block setzling = w.getBlockAt(cx, cy + 1, cz);
                setzling.setType(Material.OAK_SAPLING);
                w.generateTree(setzling.getLocation(), zufall, org.bukkit.TreeType.TREE);
            }
            case 1 -> { // Erz-Insel: Kohle + Eisen + 1 Diamant versteckt
                w.getBlockAt(cx - 1, cy, cz).setType(Material.COAL_ORE);
                w.getBlockAt(cx + 1, cy, cz - 1).setType(Material.COAL_ORE);
                w.getBlockAt(cx, cy, cz + 1).setType(Material.IRON_ORE);
                w.getBlockAt(cx + 1, cy, cz + 1).setType(Material.IRON_ORE);
                w.getBlockAt(cx, cy - 1, cz).setType(Material.DIAMOND_ORE);
            }
            case 2 -> { // Sand-Insel: Kaktus + Zuckerrohr-Basis
                w.getBlockAt(cx, cy + 1, cz).setType(Material.CACTUS);
                w.getBlockAt(cx - 1, cy, cz + 1).setType(Material.CLAY);
            }
            default -> { // Schatz-Insel
                Block kiste = w.getBlockAt(cx, cy + 1, cz);
                kiste.setType(Material.CHEST);
                if (kiste.getState() instanceof org.bukkit.block.Chest c) {
                    c.getInventory().addItem(
                        new ItemStack(Material.MELON_SEEDS, 2),
                        new ItemStack(Material.PUMPKIN_SEEDS, 2),
                        new ItemStack(Material.IRON_INGOT, 4),
                        new ItemStack(Material.GOLD_INGOT, 2),
                        new ItemStack(Material.ENDER_PEARL));
                }
            }
        }
    }

    /** Insel des Spielers (erstellt sie beim ersten Mal). */
    private Location home(Player p) {
        Integer idx = inseln.get(p.getUniqueId());
        if (idx == null) {
            idx = inseln.size();
            inseln.put(p.getUniqueId(), idx);
            baueInsel(idx);
            save();
            Bukkit.broadcast(Component.text("🌋 " + p.getName() + " hat eine neue Himmelsinsel bezogen!",
                NamedTextColor.AQUA));
        }
        return inselMitte(idx).clone().add(1, 1, 0);
    }

    /** Wem gehoert die Insel an dieser Position? (null = niemandem) */
    private UUID inselBesitzer(Location loc) {
        if (!loc.getWorld().equals(welt())) return null;
        for (Map.Entry<UUID, Integer> e : inseln.entrySet()) {
            Location mitte = inselMitte(e.getValue());
            if (Math.abs(loc.getX() - mitte.getX()) <= RADIUS
                && Math.abs(loc.getZ() - mitte.getZ()) <= RADIUS) return e.getKey();
        }
        return null;
    }

    // ────────────────────────── Items ──────────────────────────

    private ItemStack tagged(Material mat, String id, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("skyblock", id));
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

    public ItemStack inselKompass() {
        return tagged(Material.COMPASS, "insel_kompass", "Insel-Kompass", NamedTextColor.AQUA,
            List.of("Rechtsklick: zurueck zu deiner Insel", "(3 Sekunden Warmup)."));
    }

    public ItemStack voidAngel() {
        return tagged(Material.FISHING_ROD, "void_angel", "Void-Angel", NamedTextColor.LIGHT_PURPLE,
            List.of("Rechtsklick: fischt zufaellige Beute", "aus der Leere (30s Cooldown)."));
    }

    public ItemStack kernBrecher() {
        ItemStack item = tagged(Material.NETHERITE_PICKAXE, "kern_brecher", "Kern-Brecher", NamedTextColor.DARK_RED,
            List.of("Die EINZIGE Spitzhacke, die fremde",
                "Insel-Kerne brechen kann — stiehlt 25%",
                "des Insel-Levels und droppt Diamanten."));
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    // ────────────────────────── Events ──────────────────────────

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (!inseln.containsKey(p.getUniqueId())) {
            p.teleport(home(p));
            p.getInventory().addItem(inselKompass());
            p.sendMessage(Component.text("Willkommen bei Skyblock-Wars! Bau deine Insel aus — /insel hilft.",
                NamedTextColor.AQUA));
        }
    }

    /** Void-Sturz: heim statt tot. */
    @EventHandler
    public void onVoid(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) return;
        if (!(event.getEntity() instanceof Player p)) return;
        if (!inseln.containsKey(p.getUniqueId())) return;
        event.setCancelled(true);
        p.teleport(home(p));
        p.sendMessage(Component.text("Uff — die Leere hat dich wieder ausgespuckt.", NamedTextColor.GRAY));
    }

    /** Insel-Level: Bloecke auf der eigenen Insel platzieren. */
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        UUID besitzer = inselBesitzer(event.getBlock().getLocation());
        if (event.getPlayer().getUniqueId().equals(besitzer)) {
            level.merge(besitzer, 1, Integer::sum);
        }
    }

    /** Insel-Kern-Schutz + Kern-Brecher-Klau. */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() != Material.LODESTONE) return;
        UUID besitzer = inselBesitzer(event.getBlock().getLocation());
        if (besitzer == null) return;
        Player p = event.getPlayer();
        if (besitzer.equals(p.getUniqueId())) return; // eigener Kern: frei

        if (!"kern_brecher".equals(idOf(p.getInventory().getItemInMainHand()))) {
            event.setCancelled(true);
            p.sendMessage(Component.text("Fremde Insel-Kerne knackt nur der Kern-Brecher!", NamedTextColor.RED));
            return;
        }
        int beute = level.getOrDefault(besitzer, 0) / 4;
        level.merge(besitzer, -beute, Integer::sum);
        level.merge(p.getUniqueId(), beute, Integer::sum);
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(),
            new ItemStack(Material.DIAMOND, Math.min(64, Math.max(1, beute / 10))));
        save();
        String opferName = String.valueOf(Bukkit.getOfflinePlayer(besitzer).getName());
        Bukkit.broadcast(Component.text("💥 " + p.getName() + " hat den Insel-Kern von "
            + opferName + " gebrochen und " + beute + " Level gestohlen!", NamedTextColor.DARK_RED));
        p.getWorld().playSound(event.getBlock().getLocation(), Sound.ENTITY_WITHER_BREAK_BLOCK, 1f, 1f);
    }

    /** Rechtsklick-Items: Kompass + Void-Angel. */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = idOf(event.getItem());
        if (id == null) return;
        Player p = event.getPlayer();

        if (id.equals("insel_kompass")) {
            event.setCancelled(true);
            long jetzt = System.currentTimeMillis();
            Long cd = kompassCooldown.get(p.getUniqueId());
            if (cd != null && cd > jetzt) return;
            kompassCooldown.put(p.getUniqueId(), jetzt + 5_000);
            p.sendMessage(Component.text("Heimreise in 3 Sekunden — nicht bewegen!", NamedTextColor.AQUA));
            Location start = p.getLocation().clone();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (p.getLocation().distanceSquared(start) > 1.0) {
                    p.sendMessage(Component.text("Abgebrochen — du hast dich bewegt!", NamedTextColor.RED));
                    return;
                }
                p.teleport(home(p));
            }, 60L);
        }

        if (id.equals("void_angel")) {
            event.setCancelled(true);
            long jetzt = System.currentTimeMillis();
            Long cd = angelCooldown.get(p.getUniqueId());
            if (cd != null && cd > jetzt) {
                p.sendMessage(Component.text("Die Leere braucht noch " + ((cd - jetzt) / 1000 + 1) + "s.",
                    NamedTextColor.LIGHT_PURPLE));
                return;
            }
            angelCooldown.put(p.getUniqueId(), jetzt + 30_000);
            List<ItemStack> beute = List.of(
                new ItemStack(Material.COBBLESTONE, 16), new ItemStack(Material.IRON_INGOT, 3),
                new ItemStack(Material.OAK_LOG, 8), new ItemStack(Material.SAND, 8),
                new ItemStack(Material.DIAMOND), new ItemStack(Material.ENDER_PEARL, 2),
                new ItemStack(Material.GOLD_INGOT, 2), new ItemStack(Material.BONE_MEAL, 8));
            ItemStack fang = beute.get(ThreadLocalRandom.current().nextInt(beute.size())).clone();
            p.getInventory().addItem(fang).values()
                .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
            p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 0.6f);
            p.sendMessage(Component.text("Die Leere gibt her: " + fang.getAmount() + "x "
                + fang.getType().name().toLowerCase(Locale.ROOT), NamedTextColor.LIGHT_PURPLE));
        }
    }

    // ────────────────────────── /insel ──────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "home";
        switch (sub) {
            case "home", "heim" -> p.teleport(home(p));
            case "level" -> p.sendMessage(Component.text("🌋 Dein Insel-Level: "
                + level.getOrDefault(p.getUniqueId(), 0), NamedTextColor.AQUA));
            case "top" -> {
                p.sendMessage(Component.text("🌋 Top-Inseln:", NamedTextColor.AQUA));
                level.entrySet().stream()
                    .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                    .limit(5)
                    .forEach(e -> p.sendMessage(Component.text(" — "
                        + Bukkit.getOfflinePlayer(e.getKey()).getName() + ": " + e.getValue(),
                        NamedTextColor.GRAY)));
            }
            default -> p.sendMessage(Component.text("Nutzung: /insel [home|level|top]", NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length != 1) return List.of();
        return List.of("home", "level", "top").stream()
            .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
    }
}
