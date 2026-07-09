# DoofieSMP Resource Pack

Schlankes Pack **nur für die Custom-Items** — normale Items behalten ihre
Vanilla-Textur (das frühere Stempeln aller Items ist zurückgenommen; der
`ItemModelCleaner` im Plugin räumt alte Stempel automatisch weg).

## Inhalt

| Item | Model | Optik |
|---|---|---|
| Legendärer Döner | `doofie:doener` | **Eigenes 3D-Blockbench-Model** + Textur (`textures/item/doener.png`) |
| Götterspeer | `doofie:goetterspeer` | vorerst Vanilla Netherite-Speer |
| Hermes-Helm | `doofie:hermes_helm` | vorerst Vanilla Netherite-Helm |
| Hermes-Brustplatte | `doofie:hermes_brust` | vorerst Vanilla |
| Hermes-Beinschutz | `doofie:hermes_hose` | vorerst Vanilla |
| Hermes-Sandalen | `doofie:hermes_schuhe` | vorerst Vanilla |

## Installation

`DoofieSMP-Pack.zip` in den `resourcepacks`-Ordner des Clients legen und
aktivieren — oder als Server-Pack in der `server.properties` verteilen.
Ohne Pack sehen nur die 6 Custom-Items nach ihrem Basis-Item aus
(Steak/Netherite), nichts ist kaputt.

## Weitere Custom-Items umtexturieren

1. Textur ablegen: `assets/doofie/textures/item/<name>.png`
2. Model ablegen: `assets/doofie/models/item/<name>.json`
   (Blockbench-Export; `textures` auf `doofie:item/<name>` zeigen lassen)
3. Definition `assets/doofie/items/<name>.json` ersetzen durch:
   ```json
   { "model": { "type": "minecraft:model", "model": "doofie:item/<name>" } }
   ```
4. Neu zippen (Inhalt: `assets` + `pack.mcmeta`).

Pack-Format: 88 (Minecraft 26.2).
