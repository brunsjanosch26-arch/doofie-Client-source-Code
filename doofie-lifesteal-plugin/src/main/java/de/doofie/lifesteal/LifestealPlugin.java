package de.doofie.lifesteal;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Doofie-Lifesteal — Kill = Herz-Drop, Tod = Herz weg.
 *
 * Kernregeln:
 *   — Start 10 Herzen, Maximum 20.
 *   — Kill durch Spieler: Opfer verliert 1 Herz, ein Herz-Item droppt.
 *     Rechtsklick auf das Herz = +1 Herz.
 *   — 0 Herzen: Zuschauer-Modus bis /revive (Revive-Beacon noetig).
 *   — Custom Items: Herz, Herz-Fragment, Revive-Beacon, Lifesteal-Schwert
 *     (alle mit item_model 'doofie:<id>' fuers Resource Pack).
 */
public class LifestealPlugin extends JavaPlugin {

    private HeartManager heartManager;
    private LifestealItems items;

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        heartManager = new HeartManager(this);
        items = new LifestealItems(this);

        getServer().getPluginManager().registerEvents(items, this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        items.start();
        new LifestealCommands(this).register();
        new TpaCommand(this).register();

        getLogger().info("Doofie-Lifesteal aktiv — moege das Herzstehlen beginnen!");
    }

    @Override
    public void onDisable() {
        if (heartManager != null) heartManager.save();
    }

    public HeartManager getHeartManager() {
        return heartManager;
    }

    public LifestealItems getItems() {
        return items;
    }
}
