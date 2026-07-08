# Doofie Hardcore Plugin

Paper-Plugin für Minecraft **26.2** (Hardcore-Server auf exaroton) mit Kopfgeld-System,
Sell-System, Auktionshaus und Bann-Freikauf.

## Features

### Geld (Dollar)
- Jeder Spieler startet mit **500$** (Config: `start-guthaben`)
- `/money [spieler]` — Guthaben anzeigen
- `/pay <spieler> <betrag>` — Geld senden

### Sell-System (wie HugoSMP)
- `/sell` — öffnet ein Verkaufsmenü: Items beliebig reinlegen, grüner Knopf = verkaufen
- Nicht verkaufbare Items kommen automatisch zurück ins Inventar
- **Unter jedem Item im Inventar steht der Verkaufswert pro Stück** (als Lore)
- Preise stehen in der `config.yml` unter `preise` und können frei angepasst werden

### Kopfgeld
- `/kopfgeld setzen <spieler> <betrag>` — Kopfgeld setzen (Geld wird sofort abgezogen, Mindestbetrag 100$)
- `/kopfgeld liste` — alle aktiven Kopfgelder
- Mehrere Spieler können auf dasselbe Ziel setzen — die Beträge summieren sich

### Kill = Bann (nur mit Kopfgeld!)
- Wird ein Spieler **mit Kopfgeld** von einem anderen Spieler getötet:
  - Der Killer bekommt das **gesamte Kopfgeld**
  - Der **Kopf des Opfers droppt**
  - Das Opfer ist **gebannt** (Spectator-Modus beim Joinen)
- Stirbt ein Spieler **ohne Kopfgeld** (auch durch Spieler): normaler Respawn.
  Das Plugin hebt den Hardcore-Tod auf — gebannt wird man NUR über Kopfgeld.

### Freikauf (Kopfgeld + 5%)
- Gebannte können joinen, sind aber Spectator und können nur `/freikaufen`, `/money` und `/kopfgeld liste` nutzen
- `/freikaufen` — sich selbst freikaufen (kostet Kopfgeld + 5%, Config: `freikauf-aufschlag`)
- `/freikaufen <spieler>` — einen Freund freikaufen

### Auktionshaus
- `/ah` — GUI: zuerst die **dauerhaften Server-Startangebote** (Essen, Basis-Items,
  unbegrenzter Vorrat, Verkaufspreis +5%), danach die Spieler-Auktionen
- **Klick = 1 Stück kaufen** (Geld wird automatisch abgebucht, Item ins Inventar)
- **Shift+Linksklick = ganzen Stack/Bestand kaufen**
- Eigene `/ah sell`-Auktionen: Klick = Zurückziehen
- `/ah sell <preis>` — Item in der Hand einstellen, Preis gilt **pro Stück**
- Startangebote konfigurierbar in `config.yml` unter `ah-startangebot`

## Bauen

Voraussetzung: Java 21+ installiert (Gradle lädt das nötige JDK 25 automatisch herunter).

```
cd doofie-hardcore-plugin
gradle build
```

Die fertige Jar liegt dann in `build/libs/doofie-hardcore-1.0.0.jar`.

## Auf exaroton installieren

1. exaroton-Dashboard öffnen → dein Server
2. **Software**: Paper, Version **26.2** auswählen
3. **Dateien** → Ordner `plugins/` → `doofie-hardcore-1.0.0.jar` hochladen
4. Server neu starten
5. Optional: `plugins/DoofieHardcore/config.yml` anpassen (Preise, Startguthaben, Aufschlag) und `/reload confirm` oder Neustart

## Hardcore-Modus (hardcore=true)

Der Server laeuft mit **hardcore=true** (Hardcore-Herzen). Der Todesbildschirm
zeigt dann den Vanilla-Knopf "Spectate World" — das ist clientseitig und laesst
sich ohne Client-Mod nicht umbenennen. Funktional passt aber alles:
- Normale Tode: Knopf druecken -> das Plugin setzt dich sofort zurueck auf
  Survival an deinem Bett/Spawn. Nichts geht verloren.
- Kopfgeld-Tode: Knopf druecken -> du bleibst im Spectator und kannst nur
  /freikaufen nutzen. Nach dem Freikauf geht es am Bett (oder Spawn) weiter.
- Neue Spieler bekommen beim ersten Join alle Regeln und Commands erklaert.


## Voicechat (Simple Voice Chat)

Der Voicechat laeuft ueber das offizielle Simple-Voice-Chat-Plugin (liegt in `server-plugins/`).

**Installation auf exaroton:**
1. `server-plugins/voicechat-bukkit-2.6.20.jar` in den `plugins/`-Ordner hochladen
2. Server einmal starten (erstellt `plugins/voicechat/voicechat-server.properties`)
3. In dieser Datei setzen: `port=-1` (nutzt automatisch den Minecraft-Server-Port —
   noetig, weil exaroton nur einen Port freigibt)
4. Server neu starten

**Clients:** Spieler brauchen die Simple-Voice-Chat-Mod — die ist im Doofie Client
schon vorinstalliert! Ingame Standard-Taste `V` fuer das Voice-Menue, `CapsLock` = Push-to-Talk.
