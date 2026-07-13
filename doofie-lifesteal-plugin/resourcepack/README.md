# DoofieLifesteal Resource Pack

Schlankes Pack **nur für die Lifesteal-Custom-Items** — alle anderen Items
behalten ihre Vanilla-Textur. Namespace ist bewusst `lifesteal` (nicht
`doofie`), damit der `ItemModelCleaner` des Hardcore-Plugins die Items in
Ruhe lässt, falls beide Plugins auf demselben Server laufen.

## Inhalt

| Item | Model | Optik (aus NewDefault v1.82 übernommen) |
|---|---|---|
| ❤ Herz | `lifesteal:herz` | Herz-Sprite aus `gui/icons.png` (ausgeschnitten) |
| Herz-Fragment | `lifesteal:herz_fragment` | Halbes-Herz-Sprite aus `gui/icons.png` |
| Revive-Beacon | `lifesteal:revive_beacon` | Nether-Stern-Textur |
| Lifesteal-Schwert | `lifesteal:lifesteal_schwert` | Netherite-Schwert, rot eingefärbt |

Ohne Pack sehen die Items nach ihrem Basis-Item aus (roter Farbstoff /
Glitzer-Melone / Beacon / Netherite-Schwert), nichts ist kaputt.

## Installation

`DoofieLifesteal-Pack.zip` in den `resourcepacks`-Ordner des Clients legen
und aktivieren — oder als Server-Pack in der `server.properties` verteilen.

## Item umtexturieren

1. Textur ablegen: `assets/lifesteal/textures/item/<name>.png`
2. Model ablegen: `assets/lifesteal/models/item/<name>.json`
3. Definition `assets/lifesteal/items/<name>.json`:
   ```json
   { "model": { "type": "minecraft:model", "model": "lifesteal:item/<name>" } }
   ```
4. Neu zippen (Inhalt: `assets` + `pack.mcmeta`).

Pack-Format: 88 (Minecraft 26.2).
