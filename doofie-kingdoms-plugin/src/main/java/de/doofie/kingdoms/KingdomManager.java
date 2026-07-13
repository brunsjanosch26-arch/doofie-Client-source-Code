package de.doofie.kingdoms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Koenigreiche: Mitglieder, Chunk-Claims, Kriege — plus die drei Custom-Items.
 */
public class KingdomManager implements Listener, TabExecutor {

    /** Ein Koenigreich. */
    public static class Reich {
        UUID koenig;
        final Set<UUID> mitglieder = new HashSet<>();
        final Set<String> chunks = new HashSet<>();
        final Set<UUID> einladungen = new HashSet<>();
        String kriegGegen;
        long kriegEnde;
    }

    private static final long KRIEG_DAUER_MS = 10 * 60_000L;

    private final KingdomsPlugin plugin;
    private final NamespacedKey itemKey;
    private final File file;
    /** Reichsname -> Reich */
    private final Map<String, Reich> reiche = new HashMap<>();
    /** Stampfer-Cooldowns */
    private final Map<UUID, Long> stampferCooldown = new HashMap<>();

    public KingdomManager(KingdomsPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "custom_item");
        this.file = new File(plugin.getDataFolder(), "kingdoms.yml");
        load();
    }

    public void start() {
        // Kronen-Aura: alle 3s Buffs fuer Buerger nahe dem gekroenten Koenig
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player koenig : Bukkit.getOnlinePlayers()) {
                if (!"koenigskrone".equals(idOf(koenig.getInventory().getHelmet()))) continue;
                String reichName = reichVon(koenig.getUniqueId());
                if (reichName == null || !reiche.get(reichName).koenig.equals(koenig.getUniqueId())) continue;
                for (Player buerger : Bukkit.getOnlinePlayers()) {
                    if (!reichName.equals(reichVon(buerger.getUniqueId()))) continue;
                    if (buerger.getWorld() != koenig.getWorld()
                        || buerger.getLocation().distanceSquared(koenig.getLocation()) > 16 * 16) continue;
                    buerger.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 0, true, false, true));
                    buerger.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0, true, false, true));
                }
            }
        }, 60L, 60L);
    }

    // ────────────────────────── Persistenz ──────────────────────────

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        if (!yml.isConfigurationSection("reiche")) return;
        for (String name : yml.getConfigurationSection("reiche").getKeys(false)) {
            Reich r = new Reich();
            String base = "reiche." + name + ".";
            r.koenig = UUID.fromString(yml.getString(base + "koenig"));
            yml.getStringList(base + "mitglieder").forEach(s -> r.mitglieder.add(UUID.fromString(s)));
            r.chunks.addAll(yml.getStringList(base + "chunks"));
            reiche.put(name, r);
        }
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        reiche.forEach((name, r) -> {
            String base = "reiche." + name + ".";
            yml.set(base + "koenig", r.koenig.toString());
            yml.set(base + "mitglieder", r.mitglieder.stream().map(UUID::toString).toList());
            yml.set(base + "chunks", new ArrayList<>(r.chunks));
        });
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("kingdoms.yml: " + ex.getMessage());
        }
    }

    // ────────────────────────── Abfragen ──────────────────────────

    public String reichVon(UUID spieler) {
        for (Map.Entry<String, Reich> e : reiche.entrySet()) {
            if (e.getValue().mitglieder.contains(spieler)) return e.getKey();
        }
        return null;
    }

    private static String chunkKey(Chunk c) {
        return c.getWorld().getName() + ":" + c.getX() + ":" + c.getZ();
    }

    private String claimVon(Chunk c) {
        String key = chunkKey(c);
        for (Map.Entry<String, Reich> e : reiche.entrySet()) {
            if (e.getValue().chunks.contains(key)) return e.getKey();
        }
        return null;
    }

    /** Krieg zwischen den beiden Reichen aktiv? */
    private boolean imKrieg(String a, String b) {
        if (a == null || b == null) return false;
        Reich ra = reiche.get(a);
        return ra != null && b.equals(ra.kriegGegen) && ra.kriegEnde > System.currentTimeMillis();
    }

    /** Darf der Spieler diesen Block anfassen? */
    private boolean darfBauen(Player p, Block block) {
        String claim = claimVon(block.getChunk());
        if (claim == null) return true;
        String eigenes = reichVon(p.getUniqueId());
        if (claim.equals(eigenes)) return true;
        return imKrieg(claim, eigenes) || imKrieg(eigenes, claim);
    }

    // ────────────────────────── Schutz-Events ──────────────────────────

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!darfBauen(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Dieses Land gehoert einem Koenigreich!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!darfBauen(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Dieses Land gehoert einem Koenigreich!", NamedTextColor.RED));
        }
    }

    // ────────────────────────── Items ──────────────────────────

    private ItemStack tagged(Material mat, String id, String name, NamedTextColor color, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey("kingdoms", id));
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

    public ItemStack koenigskrone() {
        ItemStack item = tagged(Material.GOLDEN_HELMET, "koenigskrone", "Königskrone", NamedTextColor.GOLD,
            List.of("Nur fuer den Koenig: Buerger im",
                "16er-Umkreis bekommen Staerke I + Speed I,",
                "solange du sie traegst."));
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        meta.setEnchantmentGlintOverride(true);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack stampfer() {
        return tagged(Material.MACE, "belagerungs_stampfer", "Belagerungs-Stampfer", NamedTextColor.DARK_RED,
            List.of("Rechtsklick auf eine Wand im FEINDLICHEN",
                "Claim (nur im Krieg): bricht 3x3 Bloecke",
                "(30s Cooldown)."));
    }

    public ItemStack friedensvertrag() {
        return tagged(Material.PAPER, "friedensvertrag", "Friedensvertrag", NamedTextColor.WHITE,
            List.of("Rechtsklick (nur als Koenig): beendet",
                "alle Kriege deines Reichs sofort.",
                "Wird dabei verbraucht."));
    }

    public ItemStack byId(String id) {
        return switch (id) {
            case "koenigskrone" -> koenigskrone();
            case "belagerungs_stampfer" -> stampfer();
            case "friedensvertrag" -> friedensvertrag();
            default -> null;
        };
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        String id = idOf(event.getItem());
        if (id == null) return;
        Player p = event.getPlayer();
        String eigenes = reichVon(p.getUniqueId());

        if (id.equals("belagerungs_stampfer") && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            Block ziel = event.getClickedBlock();
            String claim = claimVon(ziel.getChunk());
            if (claim == null || claim.equals(eigenes) || !(imKrieg(claim, eigenes) || imKrieg(eigenes, claim))) {
                p.sendMessage(Component.text("Der Stampfer wirkt nur im KRIEG auf feindliche Claims!", NamedTextColor.RED));
                return;
            }
            long jetzt = System.currentTimeMillis();
            Long cd = stampferCooldown.get(p.getUniqueId());
            if (cd != null && cd > jetzt) {
                p.sendMessage(Component.text("Stampfer laedt noch " + ((cd - jetzt) / 1000 + 1) + "s.", NamedTextColor.RED));
                return;
            }
            stampferCooldown.put(p.getUniqueId(), jetzt + 30_000);
            // 3x3 Wand um den geklickten Block brechen (senkrecht zur Blickrichtung)
            boolean xAchse = Math.abs(p.getLocation().getDirection().getX())
                < Math.abs(p.getLocation().getDirection().getZ());
            for (int a = -1; a <= 1; a++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = xAchse
                        ? ziel.getRelative(a, dy, 0)
                        : ziel.getRelative(0, dy, a);
                    if (b.getType() != Material.BEDROCK && !b.getType().isAir()) b.breakNaturally();
                }
            }
            p.getWorld().playSound(ziel.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1f, 0.6f);
        }

        if (id.equals("friedensvertrag")
            && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (eigenes == null || !reiche.get(eigenes).koenig.equals(p.getUniqueId())) {
                p.sendMessage(Component.text("Nur ein KOENIG kann Frieden schliessen.", NamedTextColor.RED));
                return;
            }
            Reich r = reiche.get(eigenes);
            boolean etwasBeendet = r.kriegGegen != null && r.kriegEnde > System.currentTimeMillis();
            r.kriegGegen = null;
            for (Reich anderes : reiche.values()) {
                if (eigenes.equals(anderes.kriegGegen)) anderes.kriegGegen = null;
            }
            if (etwasBeendet) {
                event.getItem().subtract();
                Bukkit.broadcast(Component.text("🕊 " + eigenes + " hat den Friedensvertrag unterzeichnet — der Krieg ist vorbei!",
                    NamedTextColor.WHITE));
            } else {
                p.sendMessage(Component.text("Dein Reich fuehrt gerade keinen Krieg.", NamedTextColor.GRAY));
            }
        }
    }

    // ────────────────────────── /reich ──────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur fuer Spieler.");
            return true;
        }
        String sub = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "info";
        String eigenes = reichVon(p.getUniqueId());

        switch (sub) {
            case "gruenden" -> {
                if (args.length < 2) { fehler(p, "/reich gruenden <name>"); return true; }
                if (eigenes != null) { fehler(p, "Du bist schon in einem Reich (/reich verlassen)."); return true; }
                String name = args[1];
                if (reiche.containsKey(name)) { fehler(p, "Das Reich gibt es schon."); return true; }
                Reich r = new Reich();
                r.koenig = p.getUniqueId();
                r.mitglieder.add(p.getUniqueId());
                reiche.put(name, r);
                save();
                p.getInventory().addItem(koenigskrone());
                Bukkit.broadcast(Component.text("🏰 " + p.getName() + " hat das Koenigreich '" + name + "' gegruendet!",
                    NamedTextColor.GOLD));
            }
            case "einladen" -> {
                if (eigenes == null || !reiche.get(eigenes).koenig.equals(p.getUniqueId())) {
                    fehler(p, "Nur der Koenig laedt ein."); return true;
                }
                Player ziel = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                if (ziel == null) { fehler(p, "/reich einladen <spieler>"); return true; }
                reiche.get(eigenes).einladungen.add(ziel.getUniqueId());
                ziel.sendMessage(Component.text("Du wurdest ins Reich '" + eigenes
                    + "' eingeladen — /reich beitreten " + eigenes, NamedTextColor.GOLD));
                p.sendMessage(Component.text("Einladung verschickt.", NamedTextColor.GREEN));
            }
            case "beitreten" -> {
                if (eigenes != null) { fehler(p, "Du bist schon in einem Reich."); return true; }
                Reich r = args.length > 1 ? reiche.get(args[1]) : null;
                if (r == null) { fehler(p, "/reich beitreten <name>"); return true; }
                if (!r.einladungen.remove(p.getUniqueId())) {
                    fehler(p, "Du bist dort nicht eingeladen."); return true;
                }
                r.mitglieder.add(p.getUniqueId());
                save();
                Bukkit.broadcast(Component.text(p.getName() + " ist dem Reich '" + args[1] + "' beigetreten!",
                    NamedTextColor.GOLD));
            }
            case "verlassen" -> {
                if (eigenes == null) { fehler(p, "Du bist in keinem Reich."); return true; }
                Reich r = reiche.get(eigenes);
                r.mitglieder.remove(p.getUniqueId());
                if (r.koenig.equals(p.getUniqueId()) || r.mitglieder.isEmpty()) {
                    reiche.remove(eigenes);
                    Bukkit.broadcast(Component.text("🏰 Das Reich '" + eigenes + "' ist zerfallen!", NamedTextColor.RED));
                }
                save();
                p.sendMessage(Component.text("Du hast das Reich verlassen.", NamedTextColor.GRAY));
            }
            case "claim" -> {
                if (eigenes == null) { fehler(p, "Erst ein Reich gruenden/beitreten."); return true; }
                Chunk c = p.getLocation().getChunk();
                if (claimVon(c) != null) { fehler(p, "Dieser Chunk ist schon geclaimt."); return true; }
                Reich r = reiche.get(eigenes);
                if (r.chunks.size() >= 16 * r.mitglieder.size()) {
                    fehler(p, "Claim-Limit erreicht (16 pro Mitglied)."); return true;
                }
                r.chunks.add(chunkKey(c));
                save();
                p.sendMessage(Component.text("Chunk geclaimt fuer '" + eigenes + "' ("
                    + r.chunks.size() + " gesamt).", NamedTextColor.GREEN));
            }
            case "unclaim" -> {
                if (eigenes == null) { fehler(p, "Du bist in keinem Reich."); return true; }
                if (reiche.get(eigenes).chunks.remove(chunkKey(p.getLocation().getChunk()))) {
                    save();
                    p.sendMessage(Component.text("Claim aufgegeben.", NamedTextColor.GREEN));
                } else {
                    fehler(p, "Dieser Chunk gehoert nicht deinem Reich.");
                }
            }
            case "krieg" -> {
                if (eigenes == null || !reiche.get(eigenes).koenig.equals(p.getUniqueId())) {
                    fehler(p, "Nur der Koenig erklaert Krieg."); return true;
                }
                String gegner = args.length > 1 ? args[1] : null;
                if (gegner == null || !reiche.containsKey(gegner) || gegner.equals(eigenes)) {
                    fehler(p, "/reich krieg <reichsname>"); return true;
                }
                Reich r = reiche.get(eigenes);
                r.kriegGegen = gegner;
                r.kriegEnde = System.currentTimeMillis() + KRIEG_DAUER_MS;
                Bukkit.broadcast(Component.text("⚔ KRIEG! '" + eigenes + "' greift '" + gegner
                    + "' an — 10 Minuten sind die Claims beider Reiche offen!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
                Bukkit.getOnlinePlayers().forEach(pl ->
                    pl.playSound(pl.getLocation(), Sound.EVENT_RAID_HORN, 2f, 0.7f));
            }
            case "liste" -> {
                p.sendMessage(Component.text("🏰 Koenigreiche:", NamedTextColor.GOLD));
                reiche.forEach((name, r) -> p.sendMessage(Component.text(" — " + name + " ("
                    + r.mitglieder.size() + " Mitglieder, " + r.chunks.size() + " Chunks)", NamedTextColor.GRAY)));
            }
            default -> {
                if (eigenes == null) {
                    p.sendMessage(Component.text("Du bist reichlos — /reich gruenden <name>!", NamedTextColor.GRAY));
                } else {
                    Reich r = reiche.get(eigenes);
                    p.sendMessage(Component.text("🏰 " + eigenes + " — Koenig: "
                        + Bukkit.getOfflinePlayer(r.koenig).getName() + ", " + r.mitglieder.size()
                        + " Mitglieder, " + r.chunks.size() + " Chunks", NamedTextColor.GOLD));
                    if (r.kriegGegen != null && r.kriegEnde > System.currentTimeMillis()) {
                        p.sendMessage(Component.text("⚔ Im Krieg mit '" + r.kriegGegen + "' — noch "
                            + ((r.kriegEnde - System.currentTimeMillis()) / 60_000 + 1) + " Min!", NamedTextColor.RED));
                    }
                }
            }
        }
        return true;
    }

    private void fehler(Player p, String text) {
        p.sendMessage(Component.text(text, NamedTextColor.RED));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        if (args.length == 1) {
            return List.of("gruenden", "einladen", "beitreten", "verlassen", "claim", "unclaim",
                "krieg", "liste", "info").stream().filter(s -> s.startsWith(prefix)).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("krieg") || args[0].equalsIgnoreCase("beitreten"))) {
            return reiche.keySet().stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("einladen")) return null; // Spielernamen
        return List.of();
    }
}
