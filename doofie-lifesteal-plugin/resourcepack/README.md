# DoofieLifesteal Resource Pack

Schlankes Pack **nur für die Lifesteal-Custom-Items** — alle anderen Items
behalten ihre Vanilla-Textur. Namespace ist bewusst `lifesteal` (nicht
`doofie`), damit der `ItemModelCleaner` des Hardcore-Plugins die Items in
Ruhe lässt, falls beide Plugins auf demselben Server laufen.

## Inhalt

| Item | Model | Optik (aus NewDefault v1.82 übernommen) |
|---|---|---|
| ❤ Herz | `lifesteal:herz` | Volles Herz-Sprite (einheitlich rot, kein Halb-Herz-Look) |
| Herz-Fragment | `lifesteal:herz_fragment` | Halbes-Herz-Sprite aus `gui/icons.png` |
| Lifesteal-Schwert | `lifesteal:lifesteal_schwert` | **"Entangled Blade"** aus New Default+ (moderne Version, via Modrinth) inkl. Original-Model mit Hand-Transformationen |

Ohne Pack sehen die Items nach ihrem Basis-Item aus (roter Farbstoff /
Glitzer-Melone / Netherite-Schwert), nichts ist kaputt.

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
