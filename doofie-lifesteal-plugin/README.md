# Doofie-Lifesteal — Paper-Plugin (Minecraft 26.2)

Lifesteal-Modus mit Custom Items für das Doofie SMP.

## Die Regeln

- Jeder startet mit **10 Herzen**, Maximum sind **20**.
- **Killst du einen Spieler**, verliert er 1 Herz und ein **❤ Herz-Item
  droppt** am Todesort — Rechtsklick darauf gibt dir +1 Herz.
- Stirbst du **natürlich** (Lava, Fall, Mobs …), verlierst du auch 1 Herz,
  aber es droppt nichts — das Herz ist weg.
- Bei **0 Herzen** bist du **eliminiert**: Zuschauer-Modus, bis dich jemand
  mit einem Revive-Beacon per `/revive <name>` zurückholt (5 Herzen).

## Custom Items (alle mit `item_model` fürs Resource Pack)

| Item | Rezept | Effekt |
|---|---|---|
| **Herz-Fragment** | Totem der Unsterblichkeit, umringt von 8 Redstone-Blöcken | Zwischenprodukt |
| **❤ Herz** | 4 Fragmente + 4 Diamantblöcke + 1 Netherite-Barren | Rechtsklick: +1 Herz |
| **Revive-Beacon** | Beacon + 4 Herzen + 4 Netherite-Barren | In der Hand + `/revive <spieler>` |
| **Lifesteal-Schwert** | Herz / Netherite-Schwert / Totem (Spalte) | Jeder Treffer heilt 1 Herz, Schärfe V, unzerstörbar |

## Befehle

| Befehl | Beschreibung |
|---|---|
| `/herzen [spieler]` | Herzen anzeigen |
| `/auszahlen [anzahl]` | Eigene Herzen als Items auszahlen (min. 1 bleibt) |
| `/revive <spieler>` | Eliminierten zurückholen (verbraucht den Beacon) |
| `/eliminiert` | Liste aller Eliminierten |
| `/lifestealitem give <spieler> <item> [anzahl]` | Admin: Items vergeben |

## Bauen

Voraussetzung: Java 21+ (Gradle lädt JDK 25 automatisch).

```
gradle build
```

Die fertige Jar liegt in `build/libs/doofie-lifesteal-1.0.0.jar` —
einfach in den `plugins/`-Ordner des Paper-Servers (26.2) legen.

Das Resource Pack liegt unter `resourcepack/DoofieLifesteal-Pack.zip`
(Texturen aus NewDefault+ übernommen: Herz-Sprites aus `gui/icons.png`
ausgeschnitten, Nether-Stern für den Beacon, "Entangled Blade" als Schwert).
Ohne Pack funktioniert alles, die Items sehen nur nach ihrem Basis-Item aus.
