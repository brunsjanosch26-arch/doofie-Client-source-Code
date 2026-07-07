package de.doofie.hardcore;

import de.doofie.hardcore.commands.AhCommand;
import de.doofie.hardcore.commands.BountyCommand;
import de.doofie.hardcore.commands.FreikaufCommand;
import de.doofie.hardcore.commands.MoneyCommand;
import de.doofie.hardcore.commands.PayCommand;
import de.doofie.hardcore.commands.SellCommand;
import de.doofie.hardcore.listeners.AuctionGuiListener;
import de.doofie.hardcore.listeners.BanListener;
import de.doofie.hardcore.listeners.DeathListener;
import de.doofie.hardcore.listeners.LoreUpdater;
import de.doofie.hardcore.listeners.SellMenuListener;
import de.doofie.hardcore.listeners.WelcomeListener;
import de.doofie.hardcore.managers.AuctionManager;
import de.doofie.hardcore.managers.BanManager;
import de.doofie.hardcore.managers.BountyManager;
import de.doofie.hardcore.managers.EconomyManager;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

public final class HardcorePlugin extends JavaPlugin {

    private EconomyManager economy;
    private BountyManager bounties;
    private BanManager bans;
    private AuctionManager auctions;
    private LoreUpdater loreUpdater;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        economy = new EconomyManager(this);
        bounties = new BountyManager(this);
        bans = new BanManager(this);
        auctions = new AuctionManager(this);

        getCommand("money").setExecutor(new MoneyCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("sell").setExecutor(new SellCommand(this));
        getCommand("kopfgeld").setExecutor(new BountyCommand(this));
        getCommand("freikaufen").setExecutor(new FreikaufCommand(this));
        getCommand("ah").setExecutor(new AhCommand(this));

        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BanListener(this), this);
        getServer().getPluginManager().registerEvents(new AuctionGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new SellMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new WelcomeListener(this), this);

        // Verkaufswert-Lore unter jedem Item aktuell halten
        loreUpdater = new LoreUpdater(this);
        loreUpdater.start();

        // Alle 5 Minuten speichern
        getServer().getScheduler().runTaskTimer(this, this::saveAll, 20L * 300, 20L * 300);

        getLogger().info("Doofie Hardcore geladen — Kopfgeld, /sell und /ah aktiv!");
    }

    @Override
    public void onDisable() {
        saveAll();
    }

    public void saveAll() {
        economy.save();
        bounties.save();
        bans.save();
        auctions.save();
    }

    public EconomyManager economy() { return economy; }
    public BountyManager bounties() { return bounties; }
    public BanManager bans() { return bans; }
    public AuctionManager auctions() { return auctions; }

    /** Verkaufspreis pro Stueck aus der Config (0 = nicht verkaufbar). */
    public double priceOf(Material material) {
        ConfigurationSection prices = getConfig().getConfigurationSection("preise");
        if (prices == null) return 0;
        return prices.getDouble(material.name(), 0);
    }

    public static String dollar(double amount) {
        return String.format("%,.2f$", amount);
    }
}
