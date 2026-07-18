package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Struktur-Bosse: In ausgewaehlten Gross-Strukturen spawnt beim Generieren
 * ein verstaerkter Boss-Mob. Wer ihn legt, bekommt Geld + XP + Doener-Chance.
 */
public class StructureBossManager implements Listener {

    private static final String META = "doofie-strukturboss";

    /** Struktur-ID -> Boss-Typ + Anzeigename */
    private static final Map<String, BossDef> BOSSES = Map.of(
            "betterstrongholds:stronghold", new BossDef(EntityType.EVOKER, "Festungs-Vorsteher", 120),
            "betterfortresses:fortress", new BossDef(EntityType.WITHER_SKELETON, "Festungs-Kommandant", 150),
            "incendium:forbidden_castle", new BossDef(EntityType.PIGLIN_BRUTE, "Kastellan des Verbotenen", 160),
            "betterdeserttemples:desert_temple", new BossDef(EntityType.HUSK, "Pharao", 120),
            "betteroceanmonuments:ocean_monument", new BossDef(EntityType.DROWNED, "Tiefen-Waechter", 130),
            "repurposed_structures:ancient_city_end", new BossDef(EntityType.ENDERMAN, "Void-Herrscher", 180));

    private record BossDef(EntityType type, String name, double health) {}

    private final HardcorePlugin plugin;
    private final File file;
    private final Set<String> spawned = new HashSet<>();

    public StructureBossManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "strukturbosse.yml");
        load();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!event.isNewChunk()) return;
        Registry<Structure> registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        for (GeneratedStructure gs : event.getChunk().getStructures()) {
            NamespacedKey key = registry.getKey(gs.getStructure());
            if (key == null) continue;
            BossDef def = BOSSES.get(key.toString());
            if (def == null) continue;
            BoundingBox box = gs.getBoundingBox();
            int cx = (int) box.getCenterX(), cz = (int) box.getCenterZ();
            // Nur ausloesen, wenn das Zentrum in DIESEM Chunk liegt (einmal pro Struktur)
            if (cx >> 4 != event.getChunk().getX() || cz >> 4 != event.getChunk().getZ()) continue;
            String id = event.getWorld().getName() + ":" + key + ":" + cx + ":" + cz;
            if (!spawned.add(id)) continue;
            save();

            int y = (int) Math.min(box.getMaxY() - 1, Math.max(box.getCenterY(),
                    event.getWorld().getHighestBlockYAt(cx, cz)));
            Location loc = new Location(event.getWorld(), cx + 0.5, y + 1, cz + 0.5);
            event.getWorld().getChunkAtAsync(loc).thenRun(() -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                LivingEntity boss = (LivingEntity) event.getWorld().spawnEntity(loc, def.type());
                var attr = boss.getAttribute(Attribute.MAX_HEALTH);
                if (attr != null) { attr.setBaseValue(def.health()); boss.setHealth(def.health()); }
                var dmg = boss.getAttribute(Attribute.ATTACK_DAMAGE);
                if (dmg != null) dmg.setBaseValue(dmg.getBaseValue() * 1.5);
                boss.customName(Component.text(def.name(), NamedTextColor.DARK_RED));
                boss.setCustomNameVisible(true);
                boss.setRemoveWhenFarAway(false);
                boss.setPersistent(true);
                boss.setMetadata(META, new FixedMetadataValue(plugin, def.name()));
            }));
        }
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent event) {
        if (!event.getEntity().hasMetadata(META)) return;
        Player killer = event.getEntity().getKiller();
        String name = event.getEntity().getMetadata(META).get(0).asString();
        if (killer == null) return;
        double reward = plugin.getConfig().getDouble("boss-belohnung", 1500);
        plugin.economy().deposit(killer.getUniqueId(), reward);
        plugin.extras().addXp(killer, 50);
        killer.sendMessage(Component.text("BOSS BESIEGT: " + name + "! +" + HardcorePlugin.dollar(reward)
                + " und 50 XP", NamedTextColor.GOLD));
        if (Math.random() < 0.20) {
            event.getDrops().add(plugin.customItems().doener());
            killer.sendMessage(Component.text("Der Boss laesst einen Doener fallen!", NamedTextColor.YELLOW));
        }
    }

    private void load() {
        if (!file.exists()) return;
        spawned.addAll(YamlConfiguration.loadConfiguration(file).getStringList("spawned"));
    }

    private void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("spawned", new ArrayList<>(spawned));
        try {
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Konnte strukturbosse.yml nicht speichern: " + ex.getMessage());
        }
    }
}
