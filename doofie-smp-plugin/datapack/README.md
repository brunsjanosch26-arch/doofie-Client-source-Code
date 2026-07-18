# Landscapes Reimagined Genesis — Portierung auf Paper 26.2

Worldgen-Inhalte des Fabric-Modpacks "Landscapes Reimagined Genesis" (1.20.1),
portiert als Datapacks für den doofie-smp Paper-26.2-Server.
Create, Performance- und reine Client-Mods wurden bewusst weggelassen.

## Enthaltene Packs (alle nach `world/datapacks/` des SMP-Servers legen)

| Zip | Quelle | Inhalt |
|---|---|---|
| `Terralith_26.2_v2.6.4.zip` | offiziell (Stardust Labs, Modrinth) | ~100 neue Overworld-Biome, Terrain |
| `Incendium_Legacy_26.2_v5.5.0.zip` | offiziell | Nether-Biome + Strukturen (Forbidden Castle etc.) |
| `Nullscape_26.2_v1.2.20.zip` | offiziell | End-Biome + Strukturen (Dragon Skeleton etc.) |
| `t_and_t-datapack-26.x.zip` | offiziell | 60+ neue Dörfer/Outposts (Towns and Towers) |
| `LRG-Structures-Datapack.zip` | selbst konvertiert | MES 2.0.3 + MVS 5.0.11 + Repurposed Structures 7.7.5 |
| `LRG-Yungs-Datapack.zip` | selbst konvertiert | YUNG's Better Desert/Jungle Temples, Dungeons, Nether Fortresses, Ocean Monuments, Strongholds, Witch Huts (26.1.2-Jars) |

Die vier offiziellen Packs sind unverändert — bei MC-Updates einfach neue Version
von Modrinth ziehen. Wichtig: alle fünf zusammen deployen, LRG-Structures braucht
die `c:`-Biom-Tags aus den offiziellen Packs (fehlende 12 Tags liefert es selbst mit).

## Was im konvertierten Pack (LRG-Structures) angepasst wurde

Quell-Jars waren die offiziellen 26.2-Mod-Versionen (JSON-Format passte also schon);
konvertiert wurde nur, was an Fabric-Java-Code hing:

- Custom-Structure-Typen (`moogs_structures:*`, `repurposed_structures:generic_*`)
  → `minecraft:jigsaw` (Custom-Felder wie `terrain_height_radius_check` entfernt,
  `max_distance_from_center: 80`, `use_expansion_hack: false`, `size` auf 20 gekappt)
- `advanced_random_spread`-Placements (172 structure_sets) → `minecraft:random_spread`
  (verliert `min_distance_from_world_origin`)
- 126 Java-registrierte Features (Mineshaft-Deko, Minecarts, Dungeon-Räume, Supports)
  + zugehörige placed_features gelöscht; Referenzen in 94 Template-Pools durch
  `empty_pool_element` ersetzt → Mineshafts etc. generieren, aber ohne diese Details
- Custom-Prozessoren und Rules mit Custom-Rule-Tests aus processor_lists entfernt
- Pool-Gewichte > 150 proportional auf Vanilla-Maximum skaliert (64 Pools)
- Selbstreferenzierende Pool-Fallbacks → `minecraft:empty` (163 Pools)
- Entfernt (nicht als Jigsaw darstellbar): 8 RS-Mansions, 4 RS-Monuments

## Verifiziert (lokaler Paper-26.2-Testserver, Build 62)

- Boot ohne Registry-Fehler mit allen 5 Packs
- `/locate biome terralith:amethyst_rainforest` ✓ (Overworld)
- `/locate structure #towns_and_towers:town` ✓ (350 Blöcke)
- Nether: `incendium:forbidden_castle` ✓ — End: `nullscape:dragon_skeleton`,
  `mes:astral_hideaway` ✓ — Overworld: `repurposed_structures:witch_hut_oak`,
  `village_oak`, `pyramid_flower_forest` ✓

## Anpassungen im YUNG's-Pack (LRG-Yungs)

Entgegen erster Annahme sind 7 der YUNG's-Mods intern Jigsaw-basiert
(`yungsapi:yung_jigsaw` + Vanilla-Pools + NBTs) — kein Plugin-Nachbau nötig:

- `yungsapi:yung_jigsaw` → `minecraft:jigsaw` (`enhanced_terrain_adaptation` →
  `beard_thin`, `size` auf 20 gekappt, `max_distance_from_center` ≤ 116)
- `yungsapi:yung_single_element` (659 Pool-Elemente) → `single_pool_element`
  (verliert `max_count`-Limits, z.B. "max. 1 Boss-Raum")
- Custom-Placements der structure_sets → `minecraft:random_spread`
- Custom-Prozessoren (kosmetische Block-Randomizer) entfernt
- Vanilla-Pendants per leerem `has_structure`-Tag deaktiviert (Desert Pyramid,
  Jungle Temple, Swamp Hut, Nether Fortress, Ocean Monument) — Vanilla-**Stronghold
  bleibt aktiv**, damit Enderaugen/Endportal garantiert funktionieren
- Gedroppt: `betterdungeons:small_nether_dungeon` + `spider_dungeon` (Java-Generatoren),
  Better Mineshafts komplett (prozedural, keine NBTs), End Island/Bridges/Extras
  (Feature-basiert statt Structure)

Verifiziert: Desert Temple (528), Stronghold (544), Nether Fortress (592),
Witch Hut (472), Ocean Monument (1397), Zombie Dungeon (736), Jungle Temple (1010
Blöcke vom Spawn); `minecraft:desert_pyramid` nicht mehr lokalisierbar ✓

## Offen

- Kein Resourcepack nötig: alle portierten Packs nutzen ausschließlich Vanilla-Blöcke.
- Deployment auf den exaroton-SMP steht noch aus (alle 6 Zips nach `world/datapacks/`
  + Neustart; Achtung: wirkt nur auf neue Chunks). Nutzer wollte noch nicht deployen.
