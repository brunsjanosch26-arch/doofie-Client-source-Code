package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rucksack-Rueckenslot:
 * — /rucksack oeffnet ein Ausruestungs-GUI mit einem Slot; Rucksack reinlegen = tragen
 * — Shift-Rechtsklick mit Rucksack in der Hand = direkt aufsetzen/abnehmen
 * Der getragene Rucksack ist sichtbar auf dem Ruecken; Brustplatte bleibt frei.
 */
public class WornBackpackManager implements CommandExecutor, Listener {

    private static final int SLOT = 4; // Mitte der ersten Reihe

    /** Marker, damit nur unsere GUI-Klicks behandelt werden. */
    private class BackpackGuiHolder implements InventoryHolder {
        final UUID owner;
        Inventory inventory;
        BackpackGuiHolder(UUID owner) { this.owner = owner; }
        @Override public Inventory getInventory() { return inventory; }
    }

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, ItemStack> worn = new HashMap<>();
    private final Map<UUID, ItemDisplay> displays = new HashMap<>();

    public WornBackpackManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "getragene-rucksaecke.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 2L, 2L);
    }

    private boolean isBackpack(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().getKeys().stream()
                .anyMatch(k -> k.getNamespace().equalsIgnoreCase("backpackplus"));
    }

    // ===== GUI =====

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur fuer Spieler."); return true; }
        if (cmd.getName().equalsIgnoreCase("rucksackoeffnen")) {
            openWornBackpack(p);
            return true;
        }
        openGui(p);
        return true;
    }

    /**
     * Oeffnet den getragenen Rucksack (B-Taste im Doofie-Client): Der Rucksack wird
     * fuer einen Tick in die Hand gelegt und ein Rechtsklick simuliert, damit
     * BackpackPlus sein GUI oeffnet — danach kommt das Hand-Item zurueck.
     */
    private void openWornBackpack(Player p) {
        ItemStack backpack = worn.get(p.getUniqueId());
        if (backpack == null) {
            // Fallback: Rucksack in der Hand? Dann den direkt oeffnen.
            if (isBackpack(p.getInventory().getItemInMainHand())) {
                simulateOpen(p, p.getInventory().getItemInMainHand());
                return;
            }
            p.sendMessage(Component.text("Du traegst keinen Rucksack (/rucksack zum Anlegen).", NamedTextColor.RED));
            return;
        }
        ItemStack original = p.getInventory().getItemInMainHand();
        p.getInventory().setItemInMainHand(backpack);
        simulateOpen(p, backpack);
        Bukkit.getScheduler().runTask(plugin, () -> p.getInventory().setItemInMainHand(original));
    }

    private void simulateOpen(Player p, ItemStack item) {
        PlayerInteractEvent fake = new PlayerInteractEvent(p, Action.RIGHT_CLICK_AIR, item, null,
                org.bukkit.block.BlockFace.SELF, org.bukkit.inventory.EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(fake);
    }

    private void openGui(Player p) {
        BackpackGuiHolder holder = new BackpackGuiHolder(p.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, 9,
                Component.text("Ruecken-Ausruestung", NamedTextColor.DARK_AQUA));
        holder.inventory = inv;
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fm = filler.getItemMeta();
        fm.displayName(Component.text(" "));
        filler.setItemMeta(fm);
        for (int i = 0; i < 9; i++) {
            if (i != SLOT) inv.setItem(i, filler);
        }
        ItemStack current = worn.get(p.getUniqueId());
        if (current != null) inv.setItem(SLOT, current);
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackGuiHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player p)) return;

        // Klicks im eigenen Inventar: nur Shift-Klicks von Rucksaecken in den Slot umleiten
        if (event.getClickedInventory() != event.getInventory()) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                ItemStack item = event.getCurrentItem();
                if (isBackpack(item) && event.getInventory().getItem(SLOT) == null) {
                    event.getInventory().setItem(SLOT, item.clone());
                    event.setCurrentItem(null);
                }
            }
            return;
        }

        // Klicks in der GUI: nur der Rucksack-Slot ist benutzbar
        if (event.getSlot() != SLOT) {
            event.setCancelled(true);
            return;
        }
        ItemStack cursor = event.getCursor();
        if (cursor != null && !cursor.getType().isAir() && !isBackpack(cursor)) {
            event.setCancelled(true);
            p.sendMessage(Component.text("In diesen Slot passt nur ein Rucksack!", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackGuiHolder)) return;
        for (int raw : event.getRawSlots()) {
            if (raw < 9 && raw != SLOT) { event.setCancelled(true); return; }
            if (raw == SLOT && !isBackpack(event.getOldCursor())) { event.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof BackpackGuiHolder holder)) return;
        if (!(event.getPlayer() instanceof Player p)) return;
        ItemStack inSlot = event.getInventory().getItem(SLOT);
        applyWorn(p, (inSlot != null && isBackpack(inSlot)) ? inSlot.clone() : null);
        // Nicht-Rucksack-Items (falls doch eins durchrutscht) zurueckgeben
        if (inSlot != null && !isBackpack(inSlot)) {
            p.getInventory().addItem(inSlot).values()
                    .forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        }
    }

    // ===== Shift-Rechtsklick als Schnellzugriff =====

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = event.getPlayer();
        if (!p.isSneaking()) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!isBackpack(hand)) return;
        if (worn.containsKey(p.getUniqueId())) {
            p.sendMessage(Component.text("Du traegst schon einen Rucksack (/rucksack zum Verwalten).", NamedTextColor.RED));
            return;
        }
        event.setCancelled(true); // verhindert gleichzeitiges Oeffnen des Rucksacks
        applyWorn(p, hand.clone());
        p.getInventory().setItemInMainHand(null);
        p.sendMessage(Component.text("Rucksack aufgesetzt! /rucksack zum Abnehmen.", NamedTextColor.GREEN));
    }

    // ===== Kernlogik =====

    private void applyWorn(Player p, ItemStack item) {
        UUID id = p.getUniqueId();
        if (item == null) {
            if (worn.remove(id) != null) {
                removeDisplay(id);
                save();
            }
            return;
        }
        worn.put(id, item);
        spawnDisplay(p, item);
        save();
    }

    private void spawnDisplay(Player p, ItemStack item) {
        removeDisplay(p.getUniqueId());
        ItemDisplay display = p.getWorld().spawn(p.getLocation(), ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setBillboard(Display.Billboard.FIXED);
            d.setPersistent(false);
            // Versatz nach unten/hinten relativ zur Display-Rotation — sitzt wie ein Cape am Ruecken
            d.setTransformation(new Transformation(
                    new Vector3f(0f, -0.55f, 0.35f),
                    new AxisAngle4f(0f, 0f, 1f, 0f),
                    new Vector3f(1.3f, 1.3f, 1.3f),
                    new AxisAngle4f(0f, 0f, 1f, 0f)));
        });
        // Als Passagier angeheftet folgt es dem Spieler client-seitig ohne Nachziehen
        p.addPassenger(display);
        displays.put(p.getUniqueId(), display);
    }

    private void removeDisplay(UUID id) {
        ItemDisplay d = displays.remove(id);
        if (d != null && d.isValid()) d.remove();
    }

    private Location backLocation(Player p) {
        float yaw = p.getBodyYaw();
        double rad = Math.toRadians(yaw);
        double dx = Math.sin(rad) * 0.42;
        double dz = -Math.cos(rad) * 0.42;
        Location loc = p.getLocation().add(dx, 0.95, dz);
        loc.setYaw(yaw);
        loc.setPitch(0);
        return loc;
    }

    private void tick() {
        for (Map.Entry<UUID, ItemDisplay> e : displays.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            ItemDisplay d = e.getValue();
            if (p == null || !p.isOnline()) continue;
            if (!d.isValid() || !d.getWorld().equals(p.getWorld())) {
                spawnDisplay(p, worn.get(e.getKey()));
                continue;
            }
            // Nach Teleport/Dimensionswechsel wird der Passagier abgeworfen — wieder anheften
            if (!p.getPassengers().contains(d)) {
                p.addPassenger(d);
            }
            // Nur die Drehung mitfuehren, Position macht das Riding von selbst
            d.setRotation(p.getBodyYaw() + 180f, 0f);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ItemStack item = worn.get(event.getPlayer().getUniqueId());
        if (item != null) spawnDisplay(event.getPlayer(), item);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeDisplay(event.getPlayer().getUniqueId());
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            try {
                byte[] bytes = Base64.getDecoder().decode(yml.getString(key, ""));
                worn.put(UUID.fromString(key), ItemStack.deserializeBytes(bytes));
            } catch (Exception ignored) {}
        }
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<UUID, ItemStack> e : worn.entrySet()) {
            yml.set(e.getKey().toString(), Base64.getEncoder().encodeToString(e.getValue().serializeAsBytes()));
        }
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte getragene-rucksaecke.yml nicht speichern: " + ex.getMessage());
        }
    }
}
