# Full-Game Asset Mapping

This table documents the bounded asset snapshot used by `src/java-me-fullgame`. Source directories were read-only. `assets/fullgame/` contains project-local copies or explicitly documented local normalizations; no wiki images or external assets were downloaded.

Machine-readable per-file details: `reports/fullgame_asset_manifest.csv` (166 PNG rows).

## Character Mapping

| Runtime key | Project-local copy | Source family | Frames | Role | Confidence |
|---|---|---|---:|---|---|
| `character.0` | `assets/fullgame/characters/Gura/` | `spr_Gura_Idle`, `spr_Gura_Run`, `spr_Gura_portrait` | 3 idle + 6 run | Gura idle/run/portrait/weapon | High |
| `character.1` | `assets/fullgame/characters/Ame/` | `spr_Ame_idle`, `spr_Ame_run`, `spr_Ame_portrait` | 3 idle + 6 run | Amelia idle/run/portrait/weapon | High |
| `character.2` | `assets/fullgame/characters/Calli/` | `spr_Calli_idle`, `spr_Calli_run`, `spr_Calli_portrait` | 4 idle + 6 run | Calli idle/run/portrait/weapon | High |
| `character.3` | `assets/fullgame/characters/Kiara/` | `spr_Kiara_idle`, `spr_Kiara_run`, `spr_KiaraPortrait` | 4 idle + 6 run | Kiara idle/run/portrait/weapon | High |
| `character.4` | `assets/fullgame/characters/Ina/` | `spr_Ina_idle`, `spr_Ina_run`, `spr_InaPortrait` | 4 idle + 6 run | Ina idle/run/portrait/weapon | High |

All selected frames are RGBA PNGs. Original nonuniform frame sizes are preserved; the engine uses a fixed gameplay collision box independent of sprite dimensions.

## Enemy Mapping

| Runtime family | Source family | Frames | Stage role | Confidence |
|---|---|---:|---|---|
| `Shrimp` | `spr_Shrimp` | 3 | Stage 1 normal chaser | Medium |
| `Shrimp2` | `spr_Shrimp2` | 3 | Stage 1 hard chaser | Medium |
| `Sukonbu` | `spr_Sukonbu` | 3 | Stage 2 normal bomber approximation | Medium |
| `Sukonbu2` | `spr_Sukonbu2` | 3 | Stage 2 hard bomber approximation | Medium |
| `Matsurisu` | `spr_Matsurisu` | 3 | Stage 3 normal charger approximation | Medium |
| `Matsurisu3` | `spr_Matsurisu3` | 3 | Stage 3 hard charger approximation | Medium |
| `Moonafic` | `spr_Moonafic` | 3 | Stage 4 normal shooter approximation | Medium |
| `Moonafic2` | `spr_Moonafic2` | 3 | Stage 4 hard shooter approximation | Medium |
| `NousagiA-D` | `spr_NousagiEnemyA-D` | 3 each | Stage 5 chaser/shooter/charger/bomber pool | Medium |
| `Otaku` | `spr_DebuffOtakuA` | 4 | Concert Stage healer/area-effect approximation | Medium |

Enemy sprite names do not prove behavior. The local `obj_NousagiA-D` bindings are NPC bindings; the `Enemy` suffix is name-level evidence only. Behaviors in the Java catalog are explicit approximations.

## Boss Mapping

| Runtime boss | Project-local copy | Frames | Documented schedule | Asset status |
|---|---|---:|---|---|
| Fubuzilla | `assets/fullgame/bosses/fubuzilla/` | 12 | Stage 1/1H at 10:00 | Local family verified |
| Smol Ame | `assets/fullgame/bosses/AchanBoss/` | 4 | Stage 1 at 20:00 | Fallback asset approximation |
| Mikodanye | `assets/fullgame/bosses/Mikodanye/` | 8 | Stage 2/2H at 10:00 | Local family verified |
| A-chan | `assets/fullgame/bosses/AchanBoss/` | 4 | Stage 2 at 20:00 | Local family used |
| Shubangelion | `assets/fullgame/bosses/Shubangelion_walk/` | 8 | Stage 3/3H at 10:00 | Local family verified |
| Spiderchama | `assets/fullgame/bosses/Pekodam/` | 4 | Stage 3 at 20:00 | Fallback asset approximation |
| Udin | `assets/fullgame/bosses/Udin_walk/` | 6 | Stage 4/4H at 10:00 | Local family verified |
| Area 15 | `assets/fullgame/bosses/Pekodam/` | 4 | Stage 4 at 20:00 | Fallback asset approximation |
| Nanora | `assets/fullgame/bosses/Moonafic/` | 3 | Stage 5 at 10:00 | Fallback asset approximation |
| Pekodam | `assets/fullgame/bosses/Pekodam/` | 4 | Stage 5 at 20:00 | Local family verified |
| Halloween Bae | `assets/fullgame/bosses/Pekodam/` | 4 | Stage 1H at 20:00 | Fallback asset approximation |
| Harusaki Nodoka | `assets/fullgame/bosses/Mikodanye/` | 8 | Stage 2H at 20:00 | Fallback asset approximation |
| Halloween Myth | `assets/fullgame/bosses/Pekodam/` | 4 | Stage 3H at 20:00 | Fallback asset approximation |
| Goriela | `assets/fullgame/bosses/Pekodam/` | 4 | Stage 4H at 20:00 | Fallback asset approximation |

Boss schedules come from the wiki Enemy page and `data/wiki/bosses.csv`. Fallbacks are explicit rather than silently claimed as exact original art.

## Projectiles, Pickups, and Tiles

| Runtime key | Project-local path | Source | Note |
|---|---|---|---|
| `bullet` | `assets/fullgame/projectiles/bullet.png` | `spr_BulletTemp` | 7x7 player/enemy projectile proxy |
| `ame_bullet` | `assets/fullgame/projectiles/ame_bullet.png` | `spr_Ame_bullet` | Amelia projectile frame |
| `axe` | `assets/fullgame/projectiles/axe.png` | `spr_holoAxe` | Psycho Axe behavior proxy |
| `bomb` | `assets/fullgame/projectiles/bomb.png` | `spr_HoloBombIcon` | Holo Bomb behavior proxy |
| `trident` | `assets/fullgame/projectiles/trident.png` | `spr_GuraWeapon` | Trident/character weapon proxy |
| `scythe` | `assets/fullgame/projectiles/scythe.png` | `spr_CalliWeapon` | Scythe behavior proxy |
| `phoenix` | `assets/fullgame/projectiles/phoenix.png` | `spr_KiaraWeapon` | Phoenix behavior proxy |
| `tentacle` | `assets/fullgame/projectiles/tentacle.png` | `spr_InaWeapon` | Tentacle behavior proxy |
| `exp` | `assets/fullgame/pickups/spr_EXP_0..11.png` | `spr_EXP` | Twelve exported frames; identical frames are retained for compatibility |
| `coin` | `assets/fullgame/pickups/coin.png` | `spr_holoCoin` | Coin sprite frame |
| `grass` | `assets/fullgame/stages/grass.png` | `BG_grass_panel` | 32x22 tile |
| `office` | `assets/fullgame/stages/office.png` | `bg_holoOfficeFloor` | Local 128x128 normalization for memory |
| `castle` | `assets/fullgame/stages/castle.png` | `BG_castlefloor` | 128x128 tile |
| `carpet` | `assets/fullgame/stages/carpet.png` | `BG_carpet` | Local tile/fallback |
| `yagoo` | `assets/fullgame/stages/yagoo.png` | `BG_yagoohead` | Local decorative tile |
| `fantasy` | `assets/fullgame/stages/fantasy.png` | `BG_FantasyIsland` | Local 128x128 crop/resize fallback |
| `concert` | `assets/fullgame/stages/concert.png` | `bg_crowd` | Local decorative tile |
| `beach` | `assets/fullgame/stages/beach.png` | `BG_beachshore` | Unused fallback retained for expansion |

The `office` and `fantasy` files are the only deliberate derived transforms in this snapshot. They are project-local and are not written to the read-only source.

## Loading and Memory Policy

- Common character, projectile, EXP, and coin images load at startup.
- Enemy and boss animations load only for the selected stage and are released by nulling old frame arrays on stage change.
- Stage tiles load on first use.
- The JAR contains 166 PNG assets and stays below 320 KB compressed in the current build.
- Estimated worst-case decoded image memory is documented in `docs/rebuild/ASSET_INVENTORY.md`; device heap testing remains required.

## Confidence and Fallbacks

High confidence applies to direct local family existence and dimensions. Medium confidence applies where a filename and wiki page support a role but the native GameMaker behavior is unavailable. Any fallback is drawn as a colored primitive when its image is missing.
