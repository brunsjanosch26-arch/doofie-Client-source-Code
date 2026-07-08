package de.doofie.hardcore.managers;

import de.doofie.hardcore.HardcorePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Auftrags-Board: 3 taegliche Quests pro Spieler (Verkaufen / Mobs toeten). */
public class QuestManager {

    public enum Type { SELL, KILL_MOBS }

    public static class Quest {
        public final Type type;
        public final int target;
        public final double reward;
        public int progress;
        public boolean done;

        public Quest(Type type, int target, double reward, int progress, boolean done) {
            this.type = type;
            this.target = target;
            this.reward = reward;
            this.progress = progress;
            this.done = done;
        }

        public String describe() {
            return switch (type) {
                case SELL -> "Verkaufe " + target + " Items";
                case KILL_MOBS -> "Toete " + target + " Monster";
            };
        }
    }

    private final HardcorePlugin plugin;
    private final File file;
    private final Map<UUID, String> dates = new HashMap<>();
    private final Map<UUID, List<Quest>> quests = new HashMap<>();
    private final Random random = new Random();

    public QuestManager(HardcorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "quests.yml");
        load();
    }

    public List<Quest> questsOf(UUID player) {
        String today = LocalDate.now().toString();
        if (!today.equals(dates.get(player))) {
            dates.put(player, today);
            quests.put(player, generate());
        }
        return quests.get(player);
    }

    private List<Quest> generate() {
        List<Quest> list = new ArrayList<>();
        int sellTarget = 32 + random.nextInt(4) * 16;           // 32..80
        list.add(new Quest(Type.SELL, sellTarget, sellTarget * 1.5, 0, false));
        int killTarget = 5 + random.nextInt(11);                // 5..15
        list.add(new Quest(Type.KILL_MOBS, killTarget, killTarget * 12, 0, false));
        int bigSell = 128 + random.nextInt(3) * 64;             // 128..256
        list.add(new Quest(Type.SELL, bigSell, bigSell * 2.0, 0, false));
        return list;
    }

    /** Fortschritt melden — zahlt Belohnung automatisch aus. */
    public void progress(Player player, Type type, int amount) {
        for (Quest q : questsOf(player.getUniqueId())) {
            if (q.type != type || q.done) continue;
            q.progress += amount;
            if (q.progress >= q.target) {
                q.done = true;
                q.progress = q.target;
                plugin.economy().deposit(player.getUniqueId(), q.reward);
                plugin.extras().addXp(player, 50);
                player.sendMessage(Component.text()
                    .append(Component.text("QUEST GESCHAFFT! ", NamedTextColor.GREEN))
                    .append(Component.text(q.describe() + " — Belohnung: ", NamedTextColor.GRAY))
                    .append(Component.text(HardcorePlugin.dollar(q.reward), NamedTextColor.GOLD))
                    .build());
            }
        }
    }

    private void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                dates.put(uuid, yaml.getString(key + ".date", ""));
                List<Quest> list = new ArrayList<>();
                ConfigurationSection qs = yaml.getConfigurationSection(key + ".quests");
                if (qs != null) {
                    for (String i : qs.getKeys(false)) {
                        ConfigurationSection q = qs.getConfigurationSection(i);
                        if (q == null) continue;
                        list.add(new Quest(
                            Type.valueOf(q.getString("type", "SELL")),
                            q.getInt("target"),
                            q.getDouble("reward"),
                            q.getInt("progress"),
                            q.getBoolean("done")));
                    }
                }
                quests.put(uuid, list);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var entry : quests.entrySet()) {
            String key = entry.getKey().toString();
            yaml.set(key + ".date", dates.get(entry.getKey()));
            List<Quest> list = entry.getValue();
            for (int i = 0; i < list.size(); i++) {
                Quest q = list.get(i);
                String qk = key + ".quests." + i;
                yaml.set(qk + ".type", q.type.name());
                yaml.set(qk + ".target", q.target);
                yaml.set(qk + ".reward", q.reward);
                yaml.set(qk + ".progress", q.progress);
                yaml.set(qk + ".done", q.done);
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte quests.yml nicht speichern: " + e.getMessage());
        }
    }
}
