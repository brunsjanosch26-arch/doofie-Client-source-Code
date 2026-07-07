package de.doofie.hardcore.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounty-Schutz: teuer erkaufte Immunitaet gegen den Kopfgeld-Bann. */
public class ProtectionManager {

    private final Map<UUID, Long> until = new HashMap<>();

    public void protect(UUID player, long minutes) {
        until.put(player, System.currentTimeMillis() + minutes * 60_000L);
    }

    public boolean isProtected(UUID player) {
        Long t = until.get(player);
        if (t == null) return false;
        if (System.currentTimeMillis() > t) {
            until.remove(player);
            return false;
        }
        return true;
    }

    public long remainingSeconds(UUID player) {
        Long t = until.get(player);
        if (t == null) return 0;
        return Math.max(0, (t - System.currentTimeMillis()) / 1000);
    }
}
