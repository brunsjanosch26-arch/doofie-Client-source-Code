# Doofie-Netzwerk auf exaroton (wie GommeHD, nur doofer)

Ein Velocity-Proxy + Lobby + 6 Spielmodi. Jeder Modus ist ein eigener
Paper-Server (26.2) mit eigenem Plugin — Inventare und Fortschritt sind
dadurch automatisch pro Modus getrennt.

## Die Teile

| Server | Typ | Plugin-Jar |
|---|---|---|
| **proxy** | Velocity (neueste) | — (nur `velocity.toml` + `forwarding.secret`) |
| **lobby** | Paper 26.2 | `doofie-lobby-plugin/build/libs/doofie-lobby-1.0.0.jar` |
| **skyblock** | Paper 26.2 | `doofie-skyblock-plugin/.../doofie-skyblock-1.0.0.jar` |
| **bossraid** | Paper 26.2 | `doofie-bossraid-plugin/.../doofie-bossraid-1.0.0.jar` |
| **kingdoms** | Paper 26.2 | `doofie-kingdoms-plugin/.../doofie-kingdoms-1.0.0.jar` |
| **jaeger** | Paper 26.2 | `doofie-jaeger-plugin/.../doofie-jaeger-1.0.0.jar` |
| **zombie** | Paper 26.2 | `doofie-zombie-plugin/.../doofie-zombie-1.0.0.jar` |
| **chaos** | Paper 26.2 | `doofie-chaos-plugin/.../doofie-chaos-1.0.0.jar` |

## Einrichtung auf exaroton (Schritt fuer Schritt)

1. **8 Server anlegen**: 1x Software „Velocity", 7x Software „Paper" (26.2).
   Namen z.B. `doofie-proxy`, `doofie-lobby`, `doofie-skyblock`, …

2. **Proxy einrichten**:
   - `velocity.toml` aus diesem Ordner hochladen und die `[servers]`-Adressen
     durch die echten exaroton-Adressen deiner 7 Server ersetzen.
   - Beim ersten Start erzeugt Velocity die Datei `forwarding.secret` —
     ihren Inhalt kopieren.

3. **Jeden Paper-Server einrichten** (alle 7 gleich):
   - Passende Plugin-Jar in den `plugins/`-Ordner hochladen.
   - `server.properties`: `online-mode=false` (der Proxy prueft die Accounts).
   - `config/paper-global.yml`:
     ```yaml
     proxies:
       velocity:
         enabled: true
         online-mode: true
         secret: "<hier das forwarding.secret einfuegen>"
     ```
   - exaroton-Einstellung „Serverliste sichtbar" fuer die Unterserver
     ausschalten — Spieler sollen nur ueber den Proxy joinen.

4. **Starten**: erst die 7 Paper-Server, dann den Proxy.
   Spieler verbinden sich NUR mit der Proxy-Adresse!

## Wie es sich spielt

- Join → Lobby. Dort gibts den **Modus-Kompass** (Slot 5, Rechtsklick).
- Das GUI zeigt alle 6 Modi — ein Klick verbindet mit dem Server.
- Auf jedem Modus-Server: `/lobby` bringt dich zurueck.
- Ueberall dabei: das **TPA-System** — `/tpa`, `/tpahere`, `/tpaccept`,
  `/tpadeny`, `/tpaauto`, alle mit Tab-Vorschlaegen. Besonderheiten:
  - **Kingdoms**: TPA nur innerhalb des eigenen Koenigreichs.
  - **Zombie**: TPA nur tagsueber.
  - **Kopfgeldjaeger**: kein TPA zum eigenen Blutsiegel-Jagdziel.

## exaroton-Spartipp

Credits werden pro laufendem Server verbraucht. exaroton stoppt leere
Server automatisch — mit der Funktion „Proxy wakeup" (exaroton-Plugin
fuer Velocity) starten Unterserver automatisch, wenn jemand sie anwaehlt.
Alternativ: nur Proxy + Lobby dauerhaft laufen lassen und Modi-Server
manuell starten, wenn die Crew online kommt.
