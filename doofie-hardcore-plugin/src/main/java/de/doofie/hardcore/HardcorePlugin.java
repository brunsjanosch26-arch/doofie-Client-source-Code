package de.doofie.hardcore;

import de.doofie.hardcore.commands.AhCommand;
import de.doofie.hardcore.commands.BountyCommand;
import de.doofie.hardcore.commands.DailyCommand;
import de.doofie.hardcore.commands.DuellCommand;
import de.doofie.hardcore.commands.FreikaufCommand;
import de.doofie.hardcore.commands.JagdCommand;
import de.doofie.hardcore.commands.LottoCommand;
import de.doofie.hardcore.commands.MoneyCommand;
import de.doofie.hardcore.commands.PayCommand;
import de.doofie.hardcore.commands.QuestsCommand;
import de.doofie.hardcore.commands.SchutzCommand;
import de.doofie.hardcore.commands.SellCommand;
import de.doofie.hardcore.commands.TopCommand;
import de.doofie.hardcore.listeners.AuctionGuiListener;
import de.doofie.hardcore.listeners.BanListener;
import de.doofie.hardcore.listeners.DeathListener;
import de.doofie.hardcore.listeners.GameListener;
import de.doofie.hardcore.listeners.LoreUpdater;
import de.doofie.hardcore.listeners.SellMenuListener;
import de.doofie.hardcore.listeners.WelcomeListener;
import de.doofie.hardcore.managers.AuctionManager;
import de.doofie.hardcore.managers.BanManager;
import de.doofie.hardcore.managers.BountyManager;
import de.doofie.hardcore.managers.DailyManager;
import de.doofie.hardcore.managers.DuelManager;
import de.doofie.hardcore.managers.EconomyManager;
import de.doofie.hardcore.managers.LottoManager;
import de.doofie.hardcore.managers.ProtectionManager;
import de.doofie.hardcore.managers.QuestManager;
import de.doofie.hardcore.managers.StatsManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class HardcorePlugin extends JavaPlugin {

    private EconomyManager economy;
    private BountyManager bounties;
    private BanManager bans;
    private AuctionManager auctions;
    private LoreUpdater loreUpdater;
    private StatsManager stats;
    private ProtectionManager protection;
    private DuelManager duels;
    private DailyManager daily;
    private LottoManager lotto;
    private QuestManager quests;
    private NamespacedKey headKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        headKey = new NamespacedKey(this, "kopfgeld_wert");
        economy = new EconomyManager(this);
        bounties = new BountyManager(this);
        bans = new BanManager(this);
        auctions = new AuctionManager(this);
        stats = new StatsManager(this);
        protection = new ProtectionManager();
        duels = new DuelManager();
        daily = new DailyManager(this);
        lotto = new LottoManager(this);
        quests = new QuestManager(this);

        getCommand("money").setExecutor(new MoneyCommand(this));
        getCommand("pay").setExecutor(new PayCommand(this));
        getCommand("sell").setExecutor(new SellCommand(this));
        getCommand("kopfgeld").setExecutor(new BountyCommand(this));
        getCommand("freikaufen").setExecutor(new FreikaufCommand(this));
        getCommand("ah").setExecutor(new AhCommand(this));
        getCommand("jagd").setExecutor(new JagdCommand(this));
        getCommand("top").setExecutor(new TopCommand(this));
        getCommand("daily").setExecutor(new DailyCommand(this));
        getCommand("lotto").setExecutor(new LottoCommand(this));
        getCommand("duell").setExecutor(new DuellCommand(this));
        getCommand("schutz").setExecutor(new SchutzCommand(this));
        getCommand("quests").setExecutor(new QuestsCommand(this));

        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new BanListener(this), this);
        getServer().getPluginManager().registerEvents(new AuctionGuiListener(this), this);
        getServer().getPluginManager().registerEvents(new SellMenuListener(this), this);
        getServer().getPluginManager().registerEvents(new WelcomeListener(this), this);
        getServer().getPluginManager().registerEvents(new GameListener(this), this);

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
        stats.save();
        daily.save();
        lotto.save();
        quests.save();
    }

    public EconomyManager economy() { return economy; }
    public BountyManager bounties() { return bounties; }
    public BanManager bans() { return bans; }
    public AuctionManager auctions() { return auctions; }
    public StatsManager stats() { return stats; }
    public ProtectionManager protection() { return protection; }
    public DuelManager duels() { return duels; }
    public DailyManager daily() { return daily; }
    public LottoManager lotto() { return lotto; }
    public QuestManager quests() { return quests; }
    public NamespacedKey headKey() { return headKey; }

    /**
     * Preis eines konkreten Item-Stacks pro Stueck.
     * Kopf-Trophaeen aus Kopfgeld-Kills sind 10% des Kopfgelds wert.
     */
    public double priceOfItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return 0;
        if (item.getType() == Material.PLAYER_HEAD && item.hasItemMeta()) {
            Double trophy = item.getItemMeta().getPersistentDataContainer()
                .get(headKey, PersistentDataType.DOUBLE);
            if (trophy != null) return trophy;
        }
        return priceOf(item.getType());
    }

    /**
     * Verkaufspreis pro Stueck: Config-Eintrag unter 'preise' gewinnt,
     * sonst berechnet die Preis-Engine automatisch einen passenden Wert —
     * JEDES Item hat damit einen Verkaufswert.
     */
    public double priceOf(Material material) {
        ConfigurationSection prices = getConfig().getConfigurationSection("preise");
        if (prices != null && prices.contains(material.name())) {
            return prices.getDouble(material.name());
        }
        return autoPrice(material);
    }

    /** Automatische Preis-Engine: schaetzt jeden Item-Wert nach Kategorie. */
    private static double autoPrice(Material m) {
        if (!m.isItem()) return 0;
        String n = m.name();

        // Nicht im Survival erhaeltlich / Creative-only
        if (n.contains("COMMAND") || n.contains("SPAWN_EGG") || n.contains("STRUCTURE")
            || n.equals("BEDROCK") || n.equals("BARRIER") || n.equals("DEBUG_STICK")
            || n.equals("JIGSAW") || n.equals("LIGHT") || n.contains("KNOWLEDGE_BOOK")) return 0;

        // ── Besondere Einzelstuecke ──
        if (n.equals("NETHER_STAR")) return 5000;
        if (n.equals("BEACON")) return 6000;
        if (n.equals("ELYTRA")) return 2000;
        if (n.equals("TOTEM_OF_UNDYING")) return 800;
        if (n.equals("ENCHANTED_GOLDEN_APPLE")) return 500;
        if (n.equals("GOLDEN_APPLE")) return 150;
        if (n.equals("DRAGON_EGG")) return 10000;
        if (n.equals("DRAGON_HEAD")) return 1500;
        if (n.equals("WITHER_SKELETON_SKULL")) return 400;
        if (n.equals("ANCIENT_DEBRIS")) return 200;
        if (n.equals("HEART_OF_THE_SEA")) return 500;
        if (n.equals("CONDUIT")) return 900;
        if (n.equals("TRIDENT")) return 600;
        if (n.equals("MACE")) return 700;
        if (n.equals("ENDER_PEARL")) return 15;
        if (n.equals("ENDER_EYE")) return 25;
        if (n.equals("BLAZE_ROD")) return 10;
        if (n.equals("GHAST_TEAR")) return 25;
        if (n.equals("SHULKER_SHELL")) return 120;
        if (n.contains("SHULKER_BOX")) return 260;
        if (n.equals("NAME_TAG")) return 60;
        if (n.equals("SADDLE")) return 50;
        if (n.contains("SMITHING_TEMPLATE")) return 250;
        if (n.startsWith("MUSIC_DISC")) return 100;
        if (n.contains("BANNER_PATTERN")) return 40;
        if (n.equals("EXPERIENCE_BOTTLE")) return 20;
        if (n.equals("END_CRYSTAL")) return 60;
        if (n.equals("RESPAWN_ANCHOR")) return 120;
        if (n.equals("LODESTONE")) return 80;

        // ── Bloecke aus 9 Barren/Steinen ──
        if (n.equals("NETHERITE_BLOCK")) return 7200;
        if (n.equals("DIAMOND_BLOCK")) return 900;
        if (n.equals("EMERALD_BLOCK")) return 540;
        if (n.equals("GOLD_BLOCK")) return 225;
        if (n.equals("IRON_BLOCK")) return 90;
        if (n.equals("COPPER_BLOCK")) return 18;
        if (n.equals("COAL_BLOCK")) return 27;
        if (n.equals("REDSTONE_BLOCK")) return 18;
        if (n.equals("LAPIS_BLOCK")) return 36;

        // ── Ausruestung nach Material-Tier ──
        boolean gear = n.endsWith("_SWORD") || n.endsWith("_AXE") || n.endsWith("_PICKAXE")
            || n.endsWith("_SHOVEL") || n.endsWith("_HOE") || n.endsWith("_HELMET")
            || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
        if (gear) {
            if (n.startsWith("NETHERITE_")) return 1000;
            if (n.startsWith("DIAMOND_")) return 350;
            if (n.startsWith("GOLDEN_")) return 40;
            if (n.startsWith("IRON_")) return 45;
            if (n.startsWith("CHAINMAIL_")) return 60;
            if (n.startsWith("STONE_")) return 3;
            if (n.startsWith("WOODEN_")) return 1.5;
            if (n.startsWith("LEATHER_")) return 8;
            if (n.startsWith("TURTLE_")) return 120;
            return 20;
        }

        // ── Rohstoffe ──
        if (n.equals("NETHERITE_INGOT")) return 800;
        if (n.equals("NETHERITE_SCRAP")) return 180;
        if (n.equals("DIAMOND")) return 100;
        if (n.equals("EMERALD")) return 60;
        if (n.equals("GOLD_INGOT")) return 25;
        if (n.equals("IRON_INGOT")) return 10;
        if (n.equals("COPPER_INGOT")) return 2;
        if (n.equals("COAL") || n.equals("CHARCOAL")) return 3;
        if (n.equals("REDSTONE")) return 2;
        if (n.equals("LAPIS_LAZULI")) return 4;
        if (n.equals("QUARTZ")) return 4;
        if (n.equals("AMETHYST_SHARD")) return 3;
        if (n.startsWith("RAW_") && (n.contains("IRON") || n.contains("GOLD") || n.contains("COPPER")))
            return n.contains("GOLD") ? 20 : n.contains("IRON") ? 8 : 1.5;
        if (n.endsWith("_ORE")) {
            if (n.contains("DIAMOND")) return 120;
            if (n.contains("EMERALD")) return 70;
            if (n.contains("GOLD")) return 30;
            if (n.contains("ANCIENT")) return 200;
            return 12;
        }
        if (n.equals("GOLD_NUGGET")) return 3;
        if (n.equals("IRON_NUGGET")) return 1.2;
        if (n.equals("OBSIDIAN") || n.equals("CRYING_OBSIDIAN")) return 5;

        // ── Essen ──
        if (n.startsWith("COOKED_")) return 3;           // alle gebratenen Fleisch-/Fischsorten
        if (n.equals("BREAD")) return 2;
        if (n.equals("BAKED_POTATO")) return 1.5;
        if (n.equals("CAKE")) return 12;
        if (n.equals("PUMPKIN_PIE")) return 4;
        if (n.equals("GOLDEN_CARROT")) return 12;
        if (n.equals("MUSHROOM_STEW") || n.equals("RABBIT_STEW") || n.equals("BEETROOT_SOUP")) return 4;
        if (n.equals("BEEF") || n.equals("PORKCHOP") || n.equals("MUTTON") || n.equals("CHICKEN")
            || n.equals("RABBIT") || n.equals("COD") || n.equals("SALMON")) return 1;
        if (n.equals("APPLE") || n.equals("MELON_SLICE") || n.equals("SWEET_BERRIES")
            || n.equals("GLOW_BERRIES") || n.equals("CARROT") || n.equals("POTATO")
            || n.equals("BEETROOT")) return 0.5;
        if (n.equals("WHEAT") || n.equals("SUGAR_CANE") || n.equals("COCOA_BEANS")) return 1;
        if (n.equals("PUMPKIN") || n.equals("MELON")) return 1.5;
        if (n.equals("HONEY_BOTTLE") || n.equals("HONEYCOMB")) return 3;
        if (n.equals("EGG") || n.equals("SUGAR")) return 0.5;

        // ── Mob-Drops & Nuetzliches ──
        if (n.equals("BONE") || n.equals("STRING") || n.equals("SPIDER_EYE") || n.equals("ROTTEN_FLESH")) return 1;
        if (n.equals("GUNPOWDER")) return 2.5;
        if (n.equals("SLIME_BALL")) return 3;
        if (n.equals("LEATHER")) return 2;
        if (n.equals("FEATHER")) return 0.5;
        if (n.equals("ARROW")) return 0.3;
        if (n.contains("TIPPED_ARROW") || n.contains("SPECTRAL_ARROW")) return 3;
        if (n.equals("PHANTOM_MEMBRANE")) return 15;
        if (n.equals("RABBIT_FOOT") || n.equals("RABBIT_HIDE")) return 5;
        if (n.equals("INK_SAC")) return 1.5;
        if (n.equals("GLOW_INK_SAC")) return 4;
        if (n.equals("PRISMARINE_SHARD")) return 3;
        if (n.equals("PRISMARINE_CRYSTALS")) return 5;
        if (n.equals("NAUTILUS_SHELL")) return 40;
        if (n.equals("TURTLE_SCUTE") || n.equals("ARMADILLO_SCUTE") || n.equals("SCUTE")) return 20;
        if (n.equals("ECHO_SHARD")) return 60;
        if (n.equals("DISC_FRAGMENT_5")) return 25;
        if (n.equals("GLASS_BOTTLE")) return 0.3;
        if (n.contains("POTION")) return 15;
        if (n.equals("BOOK")) return 3;
        if (n.equals("ENCHANTED_BOOK")) return 80;
        if (n.equals("PAPER")) return 0.5;
        if (n.equals("TORCH") || n.equals("SOUL_TORCH")) return 0.5;

        // ── Bau-Bloecke nach Kategorie ──
        if (n.endsWith("_LOG") || n.endsWith("_STEM") || n.endsWith("_WOOD") || n.endsWith("_HYPHAE")) return 0.5;
        if (n.endsWith("_PLANKS")) return 0.15;
        if (n.endsWith("_SAPLING") || n.endsWith("_PROPAGULE")) return 0.5;
        if (n.endsWith("_WOOL") || n.endsWith("_CARPET")) return 1;
        if (n.endsWith("_BED")) return 5;
        if (n.contains("GLASS")) return 0.3;
        if (n.contains("CONCRETE") || n.contains("TERRACOTTA") || n.contains("GLAZED")) return 0.3;
        if (n.contains("SANDSTONE") || n.contains("BRICKS") || n.contains("BRICK")) return 0.3;
        if (n.equals("SAND") || n.equals("RED_SAND") || n.equals("GRAVEL") || n.equals("CLAY_BALL")) return 0.1;
        if (n.equals("CLAY")) return 0.4;
        if (n.equals("DIRT") || n.equals("COARSE_DIRT") || n.equals("ROOTED_DIRT")
            || n.equals("GRASS_BLOCK") || n.equals("MUD") || n.equals("NETHERRACK")) return 0.02;
        if (n.equals("COBBLESTONE") || n.equals("COBBLED_DEEPSLATE") || n.equals("STONE")
            || n.equals("DEEPSLATE") || n.equals("ANDESITE") || n.equals("DIORITE")
            || n.equals("GRANITE") || n.equals("TUFF") || n.equals("BLACKSTONE")
            || n.equals("BASALT") || n.equals("END_STONE")) return 0.05;
        if (n.contains("CORAL")) return 2;
        if (n.contains("FLOWER") || n.contains("TULIP") || n.contains("ORCHID")
            || n.contains("DANDELION") || n.contains("POPPY")) return 0.5;
        if (n.endsWith("_DYE")) return 0.8;
        if (n.endsWith("_SEEDS")) return 0.2;
        if (n.contains("BUCKET")) return 12;
        if (n.endsWith("_BOAT") || n.endsWith("_RAFT") || n.contains("MINECART")) return 4;
        if (n.contains("RAIL")) return 3;
        if (n.equals("GLOWSTONE_DUST")) return 2;
        if (n.equals("GLOWSTONE")) return 8;
        if (n.equals("SHROOMLIGHT") || n.equals("SEA_LANTERN")) return 6;
        if (n.contains("HEAD") || n.contains("SKULL")) return 100;

        // Fallback: jedes andere Item hat einen kleinen Grundwert
        return 0.25;
    }

    public static String dollar(double amount) {
        return String.format("%,.2f$", amount);
    }
}
