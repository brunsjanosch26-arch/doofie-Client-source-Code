package de.doofie.hardcore.listeners;

import de.doofie.hardcore.HardcorePlugin;
import de.doofie.hardcore.managers.ShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.data.type.WallSign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Kistenshops: Wandschild an einer Kiste mit "[shop]" in Zeile 1
 * und dem Preis pro Item in Zeile 2. Rechtsklick = 1 Item kaufen.
 */
public class ShopListener implements Listener {

    private final HardcorePlugin plugin;

    public ShopListener(HardcorePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onSignChange(SignChangeEvent event) {
        String line0 = PlainTextComponentSerializer.plainText().serialize(event.line(0) != null ? event.line(0) : Component.empty());
        if (!line0.equalsIgnoreCase("[shop]")) return;
        Player player = event.getPlayer();

        Block signBlock = event.getBlock();
        Block chestBlock = attachedChest(signBlock);
        if (chestBlock == null) {
            player.sendMessage(Component.text("Das Schild muss an einer Kiste haengen!", NamedTextColor.RED));
            return;
        }
        double price;
        try {
            String line1 = PlainTextComponentSerializer.plainText().serialize(event.line(1) != null ? event.line(1) : Component.empty());
            price = Double.parseDouble(line1.replace(",", ".").replace("$", "").trim());
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Zeile 2 muss der Preis pro Item sein, z.B. 25", NamedTextColor.RED));
            return;
        }
        if (price <= 0) return;

        plugin.shops().add(new ShopManager.Shop(player.getUniqueId(), player.getName(), price,
            signBlock.getLocation(), chestBlock.getLocation()));

        event.line(0, Component.text("[Shop]", NamedTextColor.DARK_GREEN));
        event.line(1, Component.text(HardcorePlugin.dollar(price) + "/Stueck", NamedTextColor.GOLD));
        event.line(2, Component.text(player.getName(), NamedTextColor.GRAY));
        event.line(3, Component.text("Rechtsklick = Kauf", NamedTextColor.GRAY));
        player.sendMessage(Component.text("Shop erstellt! Leg Ware in die Kiste — verkauft wird auch offline.", NamedTextColor.GREEN));
    }

    private Block attachedChest(Block sign) {
        if (sign.getBlockData() instanceof WallSign ws) {
            Block behind = sign.getRelative(ws.getFacing().getOppositeFace());
            if (behind.getType() == Material.CHEST || behind.getType() == Material.BARREL) return behind;
        }
        Block below = sign.getRelative(0, -1, 0);
        if (below.getType() == Material.CHEST || below.getType() == Material.BARREL) return below;
        return null;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();

        // Kiste eines Shops: nur der Besitzer darf sie oeffnen
        ShopManager.Shop chestShop = plugin.shops().byChest(block.getLocation());
        if (chestShop != null && !chestShop.owner().equals(player.getUniqueId())) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Diese Kiste gehoert " + chestShop.ownerName()
                + " — kauf ueber das Schild!", NamedTextColor.RED));
            return;
        }

        // Schild: Kauf abwickeln
        ShopManager.Shop shop = plugin.shops().bySign(block.getLocation());
        if (shop == null) return;
        event.setCancelled(true);

        if (shop.owner().equals(player.getUniqueId())) {
            player.sendMessage(Component.text("Dein Shop — Preis: " + HardcorePlugin.dollar(shop.price()) + "/Stueck", NamedTextColor.GREEN));
            return;
        }
        if (!(shop.chest().getBlock().getState() instanceof Chest chest)) {
            player.sendMessage(Component.text("Die Shop-Kiste fehlt!", NamedTextColor.RED));
            return;
        }
        ItemStack ware = null;
        for (ItemStack item : chest.getBlockInventory().getContents()) {
            if (item != null && !item.getType().isAir()) { ware = item; break; }
        }
        if (ware == null) {
            player.sendMessage(Component.text("Der Shop ist ausverkauft!", NamedTextColor.RED));
            return;
        }
        int amount = player.isSneaking() ? ware.getAmount() : 1;
        double cost = shop.price() * amount;
        if (!plugin.economy().withdraw(player.getUniqueId(), cost)) {
            player.sendMessage(Component.text("Nicht genug Geld! (" + HardcorePlugin.dollar(cost) + ")", NamedTextColor.RED));
            return;
        }
        ItemStack bought = ware.clone();
        bought.setAmount(amount);
        ware.setAmount(ware.getAmount() - amount);
        plugin.economy().deposit(shop.owner(), cost);
        var leftover = player.getInventory().addItem(bought);
        leftover.values().forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
        player.sendMessage(Component.text("Gekauft: " + amount + "x " + bought.getType().name()
            + " fuer " + HardcorePlugin.dollar(cost) + " (Shift+Klick = ganzer Stack)", NamedTextColor.GREEN));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Location loc = event.getBlock().getLocation();
        ShopManager.Shop shop = plugin.shops().bySign(loc);
        if (shop == null) shop = plugin.shops().byChest(loc);
        if (shop == null) return;
        if (!shop.owner().equals(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("Das ist der Shop von " + shop.ownerName() + "!", NamedTextColor.RED));
        } else {
            plugin.shops().remove(shop.sign());
            event.getPlayer().sendMessage(Component.text("Shop aufgeloest.", NamedTextColor.YELLOW));
        }
    }
}
