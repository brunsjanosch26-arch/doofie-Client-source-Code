# Doofie Hardcore Plugin

Paper-Plugin für Minecraft **26.2** (Hardcore-Server auf exaroton) mit Kopfgeld-System,
Sell-System, Auktionshaus und Bann-Freikauf.

## Features

### Geld (Dollar)
- Jeder Spieler startet mit **500$** (Config: `start-guthaben`)
- `/money [spieler]` — Guthaben anzeigen
- `/pay <spieler> <betrag>` — Geld senden

### Sell-System (wie HugoSMP)
- `/sell hand` — Item in der Hand verkaufen
- `/sell all` — alles Verkaufbare im Inventar verkaufen
- Preise stehen in der `config.yml` unter `preise` und können frei angepasst werden
- **Verkaufte Items landen automatisch mit +5% Aufschlag im /ah** (Config: `ah-aufschlag`) —
  andere Spieler können sie dort zurückkaufen

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
- `/ah` — GUI mit allen Angeboten: Item mit "Verkauft von" und Preis pro Stück
- **Klick = 1 Stück kaufen** (Geld wird automatisch abgebucht, Item ins Inventar)
- **Shift+Linksklick = kompletten Bestand kaufen**
- Eigene `/ah sell`-Auktionen: Klick = Zurückziehen
- `/ah sell <preis>` — Item in der Hand einstellen, Preis gilt **pro Stück**
  (max. 5 gleichzeitig, Config: `max-auktionen-pro-spieler`)

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

## Wichtig für Hardcore

Die Welt kann als Hardcore laufen (Herzen-Optik) — das Plugin setzt Spieler ohne
Kopfgeld-Bann nach dem Tod automatisch zurück in den Survival-Modus.
Gebannte bleiben Spectator, bis sie freigekauft werden.
