package de.doofie.hardcore.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Duelle mit Geld-Einsatz: Herausforderung, aktives Duell, Pot. */
public class DuelManager {

    public record Request(UUID challenger, double stake, long time) {}
    public record ActiveDuel(UUID a, UUID b, double pot) {}

    /** Ziel -> offene Herausforderung */
    private final Map<UUID, Request> requests = new HashMap<>();
    /** Spieler -> aktives Duell (beide Spieler zeigen auf dasselbe Objekt) */
    private final Map<UUID, ActiveDuel> active = new HashMap<>();

    public void request(UUID challenger, UUID target, double stake) {
        requests.put(target, new Request(challenger, stake, System.currentTimeMillis()));
    }

    /** Offene Herausforderung fuer dieses Ziel (max. 60s alt). */
    public Request pendingFor(UUID target) {
        Request r = requests.get(target);
        if (r == null) return null;
        if (System.currentTimeMillis() - r.time() > 60_000L) {
            requests.remove(target);
            return null;
        }
        return r;
    }

    public void startDuel(UUID a, UUID b, double pot) {
        requests.remove(a);
        requests.remove(b);
        ActiveDuel duel = new ActiveDuel(a, b, pot);
        active.put(a, duel);
        active.put(b, duel);
    }

    public ActiveDuel duelOf(UUID player) {
        return active.get(player);
    }

    public boolean inDuel(UUID player) {
        return active.containsKey(player);
    }

    public void endDuel(ActiveDuel duel) {
        active.remove(duel.a());
        active.remove(duel.b());
    }
}
