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
import java.util.Locale;
import java.util.Map;

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

    /** Anzeigename, Server-Name (velocity.toml), exaroton-ID, Icon, Beschreibung. */
    private record Modus(String titel, String server, String exarotonId, Material icon, String beschreibung) {}

    private static final List<Modus> MODI = List.of(
        new Modus("SkyWars-Duell", "skyblock", "Qdj4Y8IcnMTNAUcV", Material.GRASS_BLOCK,
            "1v1: Spawner farmen, Haendler, Mittel-Insel-Fight!"),
        new Modus("Kopfgeldjäger 2.0", "jaeger", "70wHXEJ4CRBeKE09", Material.SPYGLASS,
            "Jeder Kill macht dich wertvoller..."),
        new Modus("Zombie-Apokalypse", "zombie", "73GTtALKoxyJx2kT", Material.ZOMBIE_HEAD,
            "Nachts kommen die Horden. Blutmond alle 7 Tage!"),
        new Modus("Chaos-Events", "chaos", "lxhKsPZ57twbtxAY", Material.AMETHYST_SHARD,
            "Alle 15 Minuten wuerfelt der Server."),
        new Modus("Doofie-SMP", "smp", "jY7FAlQkUJJHYBY7", Material.OAK_LOG,
            "Klassisches Survival — bau dein Reich!"),
        new Modus("Lifesteal", "lifesteal", "7a8tYvDeMn1wSVlH", Material.RED_DYE,
            "Kill = Herz-Drop. 0 Herzen = Zuschauer!"));

    /** Die 1v1-Duell-Server fuer /duel <kit>. */
    private static final Map<String, Modus> DUELL = Map.of(
        "sword", new Modus("OnlySword-Duell", "onlysword", "isHqT85sjXIgj4wG", Material.DIAMOND_SWORD,
            "1v1 mit Diamant-Kit"),
        "uhc", new Modus("UHC-Duell", "uhckit", "boC9Gur8yjQ5rC90", Material.GOLDEN_APPLE,
            "1v1 mit UHC-Kit"));

    private NamespacedKey kompassKey;
    private NamespacedKey serverKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
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
                if (e instanceof org.bukkit.entity.LivingEntity && !(e instanceof Player)
                    && e.getType() != org.bukkit.entity.EntityType.ARMOR_STAND
                    && !e.hasMetadata("NPC")) e.remove();
            }
        }
        new SchemCommand(this).register();

        // /duel <sword|uhc> — direkt in die Arena; /duel annehmen|ablehnen als Antwort
        getCommand("duel").setExecutor((sender, cmd, label, args) -> {
            if (!(sender instanceof Player p)) return true;
            String arg = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
            if (arg.equals("annehmen") || arg.equals("accept")) {
                duellAnnehmen(p);
                return true;
            }
            if (arg.equals("ablehnen") || arg.equals("deny")) {
                duellAblehnen(p);
                return true;
            }
            Modus ziel = DUELL.get(arg);
            if (ziel == null) {
                p.sendMessage(Component.text("⚔ Schlage einen Spieler fuer ein Duell — oder: /duel sword | /duel uhc | /duel annehmen | /duel ablehnen",
                    NamedTextColor.RED));
                return true;
            }
            wakeUndVerbinde(p, ziel, ziel.server());
            return true;
        });
        getCommand("duel").setTabCompleter((sender, cmd, label, args) ->
            args.length == 1 ? java.util.stream.Stream.of("sword", "uhc", "annehmen", "ablehnen")
                .filter(k -> k.startsWith(args[0].toLowerCase(Locale.ROOT))).toList() : List.of());
        registriereDuellAntworten();

        // Offizieller Welt-Spawn: die gebaute Lobby bei 387 / -23 / 137
        org.bukkit.World w = getServer().getWorlds().get(0);
        org.bukkit.Location spawn = new org.bukkit.Location(w, 387.5, -23, 137.5);
        w.setSpawnLocation(spawn);

        getLogger().info("Doofie-Lobby aktiv — " + MODI.size() + " Modi im Kompass, ewiger Tag, keine Mobs.");
    }

    /** Void-Rettung: Wer unter y=-50 faellt, landet wieder am Spawn. */
    @EventHandler
    public void onVoid(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event.getTo().getY() < -50) {
            Player p = event.getPlayer();
            p.teleport(p.getWorld().getSpawnLocation().clone().add(0, 1, 0));
            p.setFallDistance(0);
        }
    }

    /** Absolut nichts spawnt in der Lobby — auch keine Slimes aus Superflat-Chunks.
     *  Armor Stands sind ausgenommen: die werden gezielt per /summon als Deko
     *  platziert (SpawnReason.COMMAND), sind harmlos (keine KI) und sollen bleiben. */
    @EventHandler
    public void onSpawn(org.bukkit.event.entity.CreatureSpawnEvent event) {
        if (event.getEntityType() == org.bukkit.entity.EntityType.ARMOR_STAND) return;
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
        p.teleport(p.getWorld().getSpawnLocation().clone().add(0.5, 0, 0.5));
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

    // ────────────────────────── GommeHD-Duell: Schlagen -> Kit -> Anfrage ──────────────────────────

    /** Kit-Auswahl-GUI nach dem Schlagen: merkt sich das Ziel. */
    private static class DuellKitHolder implements InventoryHolder {
        final java.util.UUID ziel;
        DuellKitHolder(java.util.UUID ziel) { this.ziel = ziel; }
        @Override
        public Inventory getInventory() { return null; }
    }

    /** Offene Duell-Anfragen: Ziel -> (Herausforderer, Kit, Ablauf-Zeit). */
    private record DuellAnfrage(java.util.UUID herausforderer, String kit, long ablauf) {}
    private final java.util.Map<java.util.UUID, DuellAnfrage> anfragen = new java.util.HashMap<>();

    /** Spieler schlaegt Spieler in der Lobby -> Kit-Auswahl oeffnen. */
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onSchlag(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player angreifer)) return;
        if (!(event.getEntity() instanceof Player ziel)) return;
        event.setCancelled(true);
        Inventory gui = Bukkit.createInventory(new DuellKitHolder(ziel.getUniqueId()), 9,
            Component.text("⚔ Duell gegen " + ziel.getName() + " — Kit?",
                NamedTextColor.DARK_RED, TextDecoration.BOLD));
        gui.setItem(3, duellIcon(Material.DIAMOND_SWORD, "OnlySword",
            "Volle Dia-Ruestung, Schwert, Steaks"));
        gui.setItem(5, duellIcon(Material.GOLDEN_APPLE, "UHC",
            "Eisen-Kit, Bogen, Golden Head, Eimer — keine Regen!"));
        angreifer.openInventory(gui);
        angreifer.playSound(angreifer.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.4f);
    }

    private ItemStack duellIcon(Material mat, String name, String beschreibung) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.AQUA, TextDecoration.BOLD)
            .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(beschreibung, NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false)));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onKitWahl(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof DuellKitHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player herausforderer)) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        String kit = item.getType() == Material.GOLDEN_APPLE ? "uhc" : "sword";
        herausforderer.closeInventory();

        Player ziel = Bukkit.getPlayer(holder.ziel);
        if (ziel == null) {
            herausforderer.sendMessage(Component.text("Der Spieler ist nicht mehr da.", NamedTextColor.RED));
            return;
        }
        anfragen.put(ziel.getUniqueId(), new DuellAnfrage(herausforderer.getUniqueId(), kit,
            System.currentTimeMillis() + 30_000));
        herausforderer.sendMessage(Component.text("⚔ Anfrage an " + ziel.getName()
            + " gesendet (" + kit.toUpperCase() + ", 30s gueltig).", NamedTextColor.GREEN));
        ziel.sendMessage(Component.text()
            .append(Component.text("⚔ DUELL! ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text(herausforderer.getName() + " fordert dich heraus ("
                + kit.toUpperCase() + "-Kit): ", NamedTextColor.GOLD))
            .append(Component.text("[✔ ANNEHMEN]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/duellannehmen")))
            .append(Component.text("  "))
            .append(Component.text("[✖ ABLEHNEN]", NamedTextColor.RED, TextDecoration.BOLD)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/duellablehnen")))
            .build());
        ziel.playSound(ziel.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
    }

    private void duellAnnehmen(Player p) {
        DuellAnfrage anfrage = anfragen.remove(p.getUniqueId());
        if (anfrage == null || anfrage.ablauf() < System.currentTimeMillis()) {
            p.sendMessage(Component.text("Keine offene Duell-Anfrage.", NamedTextColor.RED));
            return;
        }
        Player gegner = Bukkit.getPlayer(anfrage.herausforderer());
        if (gegner == null) {
            p.sendMessage(Component.text("Der Herausforderer ist weg.", NamedTextColor.RED));
            return;
        }
        starteDuell(gegner, p, anfrage.kit());
    }

    private void duellAblehnen(Player p) {
        DuellAnfrage anfrage = anfragen.remove(p.getUniqueId());
        if (anfrage != null) {
            Player gegner = Bukkit.getPlayer(anfrage.herausforderer());
            if (gegner != null) gegner.sendMessage(Component.text(
                p.getName() + " hat dein Duell abgelehnt.", NamedTextColor.RED));
            p.sendMessage(Component.text("Duell abgelehnt.", NamedTextColor.GRAY));
        } else {
            p.sendMessage(Component.text("Keine offene Duell-Anfrage.", NamedTextColor.RED));
        }
    }

    private void registriereDuellAntworten() {
        getCommand("duellannehmen").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player p) duellAnnehmen(p);
            return true;
        });
        getCommand("duellablehnen").setExecutor((sender, cmd, label, args) -> {
            if (sender instanceof Player p) duellAblehnen(p);
            return true;
        });
    }

    /** Weckt notfalls den Kit-Server und schickt beide rueber, sobald er online ist. */
    private void starteDuell(Player a, Player b, String kit) {
        Modus arena = DUELL.get(kit);
        String token = getConfig().getString("exaroton-token", "");
        for (Player p : List.of(a, b)) {
            p.sendMessage(Component.text("⚔ Duell angenommen — Arena wird vorbereitet...", NamedTextColor.GOLD));
        }
        if (token.isEmpty()) {
            verbinde(a, arena.server());
            verbinde(b, arena.server());
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            if (exarotonStatus(token, arena.exarotonId()) == 1) {
                Bukkit.getScheduler().runTask(this, () -> {
                    verbinde(a, arena.server());
                    verbinde(b, arena.server());
                });
                return;
            }
            exarotonStart(token, arena.exarotonId());
            Bukkit.getScheduler().runTask(this, () -> {
                for (Player p : List.of(a, b)) {
                    if (p.isOnline()) p.sendMessage(Component.text(
                        "⏳ Die Arena wird geweckt — ihr werdet AUTOMATISCH verbunden (~1 Min)...",
                        NamedTextColor.GOLD));
                }
            });
            // Alle 5s pruefen, max. 3 Minuten
            for (int versuch = 0; versuch < 36; versuch++) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ex) {
                    return;
                }
                if (exarotonStatus(token, arena.exarotonId()) == 1) {
                    Bukkit.getScheduler().runTaskLater(this, () -> {
                        if (a.isOnline()) verbinde(a, arena.server());
                        if (b.isOnline()) verbinde(b, arena.server());
                    }, 60L); // 3s Puffer, bis der Server wirklich annimmt
                    return;
                }
            }
        });
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
        Modus modus = MODI.stream().filter(m -> m.server().equals(server)).findFirst().orElse(null);
        wakeUndVerbinde(p, modus, server);
    }

    /** Verbindet — und weckt den Server vorher per exaroton-API, falls er schlaeft. */
    private void wakeUndVerbinde(Player p, Modus modus, String server) {
        String token = getConfig().getString("exaroton-token", "");
        if (modus != null && !token.isEmpty()) {
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                int status = exarotonStatus(token, modus.exarotonId());
                if (status == 1) {
                    Bukkit.getScheduler().runTask(this, () -> verbinde(p, server));
                } else {
                    exarotonStart(token, modus.exarotonId());
                    Bukkit.getScheduler().runTask(this, () -> {
                        p.sendMessage(Component.text("⏳ " + modus.titel()
                            + " schlaeft gerade — ich habe ihn GEWECKT! "
                            + "Versuch es in ~1 Minute nochmal.", NamedTextColor.GOLD));
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1f);
                    });
                }
            });
        } else {
            verbinde(p, server);
        }
    }

    private void verbinde(Player p, String server) {
        if (!p.isOnline()) return;
        p.sendMessage(Component.text("Verbinde mit " + server + "...", NamedTextColor.AQUA));
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        p.sendPluginMessage(this, "BungeeCord", out.toByteArray());
    }

    /** exaroton-Status: 1=online, 0=aus, 2=startet, -1=Fehler. */
    private int exarotonStatus(String token, String serverId) {
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.exaroton.com/v1/servers/" + serverId + "/"))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "doofie-lobby").build();
            String body = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            var status = com.google.gson.JsonParser.parseString(body)
                .getAsJsonObject().getAsJsonObject("data").get("status");
            return status.getAsInt();
        } catch (Exception ex) {
            return -1;
        }
    }

    private void exarotonStart(String token, String serverId) {
        try {
            var client = java.net.http.HttpClient.newHttpClient();
            var req = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("https://api.exaroton.com/v1/servers/" + serverId + "/start/"))
                .header("Authorization", "Bearer " + token)
                .header("User-Agent", "doofie-lobby").build();
            client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) { }
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
