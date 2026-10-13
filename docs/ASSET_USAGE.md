# Asset Usage

Source of truth: verified local asset references, asset metadata, and asset policy for HoloJava.

See `docs/ASSET_LOADER_CONTRACT.md` for resolution, ownership, frame validation, and disposable-resource rules.

## Local Asset Root

- Original extracted assets remain in `HoloCure_Unpacked` and are treated as read-only.
- Asset paths in this catalog are relative to the extraction root.
- Code or tools must receive the extraction root through configuration or an environment variable named `HOLOJAVA_ASSET_ROOT`.
- Local development may default to `../HoloCure_Unpacked`, but no absolute home-directory path is treated as portable configuration.

## Metadata Verification

Metadata was verified by two methods:

1. The existing `reports/asset_manifest.csv` report.
2. Direct filesystem and image/Audio checks with Python/Pillow and ffprobe.

The resolved metadata in this file is verified. Animation ordering, intended gameplay use, and branch-specific meaning remain assumptions unless otherwise marked.

## First Vertical Slice Selected Assets

All paths are relative to the `HoloCure_Unpacked` root. Only a single existing file is required for each single-frame asset; multi-frame assets contain consecutive numbered files. Frame-number order is exporter/file-order metadata, not proof of the intended animation order.

| Asset Key | Relative Path Pattern | Frames | Verified Dimensions | Notes |
|---|---|---:|---:|---|
| `player` | `sprites/testPlayer/testPlayer_0.png` | 1 | 64×64 | Placeholder-style player proxy for initial movement testing only. |
| `enemy.basic` | `sprites/testEnemy/testEnemy_0.png` | 1 | 37×55 | Single-frame enemy proxy. |
| `projectile.basic` | `sprites/spr_BulletTemp/spr_BulletTemp_0.png` | 1 | 7×7 | Minimal projectile proxy. |
| `pickup.xp` | `sprites/spr_EXP/spr_EXP_0.png` through `spr_EXP_11.png` | 12 | 12×13 each | Existing animated XP frame set. |
| `effect.pickup` | `sprites/spr_fightpitPickUp_Attack/spr_fightpitPickUp_Attack_0.png` through `_5.png` | 6 | 21×21 each | Stable effect-frame dimensions; gameplay meaning is not verified. |
| `ui.pickup_icon` | `sprites/hud_pickupicon/hud_pickupicon_0.png` | 1 | 16×16 | Small HUD marker candidate. |
| `ui.digit_white` | `sprites/ui_digit_white/ui_digit_white_0.png` through `_9.png` | 10 | 5×7 each | Numeric display glyphs. |
| `ui.level_header` | `sprites/ui_level_header_white/ui_level_header_white_0.png` | 1 | 20×9 | Header element. |
| `arena.tile` | `sprites/BG_grass_panel/BG_grass_panel_0.png` | 1 | 32×22 | Small reusable candidate tile. |
| `effect.explosion` | `sprites/spr_Explosion/spr_Explosion_0.png` through `_8.png` | 9 | Mixed from 24×24 to 114×118 | Available, but frame sizes are not uniform; must be inspected before use. |

## Candidate Audio

| Asset Key | Relative Path | Verified Format | Duration |
|---|---|---|---:|
| `sfx.shot` | `sounds/sfx/snd_bullet.wav` | PCM WAV, 44100 Hz, mono | 0.267823 |
| `sfx.hit` | `sounds/sfx/snd_hit1.wav` | PCM WAV, 44100 Hz, mono | 0.185760 |
| `sfx.xp_collect` | `sounds/sfx/snd_getEXP.wav` | PCM WAV, 44100 Hz, mono | 0.390884 |
| `sfx.impact` | `sounds/sfx/snd_explosion.wav` | PCM WAV, 44100 Hz, mono | 0.317188 |
| `bgm.arena` | `sounds/bgm/bgm_candygoround.ogg` | Ogg Vorbis, 48000 Hz, stereo | 157.665188 |

## Unresolved and Temporary Items

- `effect.pickup` is a stable six-frame effect, but its intended meaning is unverified.
- `effect.explosion` has non-uniform exported frame sizes and needs review before use.
- Player and enemy candidates are proxies only and may not be suitable as final art direction.
- Animation frame ordering is limited to observed filenames and exported frame indices; intended gameplay animation is not verified.
- No candidate selection establishes redistribution permission.
- If any asset is unsuitable during implementation, use a clearly documented placeholder instead of inventing an unverified path.

## Licensing and Publication Risk

- Candidate assets are referenced from the local extraction only.
- Original game content may be protected by copyright or separately licensed.
- Public distribution requires explicit permission review for these assets.
- Removing or replacing assets must preserve the same asset-key contract.

## Placeholder Replacement Strategy

If a verified asset is later rejected during development:

1. Keep the asset key.
2. Replace only the mapped relative path and metadata.
3. Mark the new entry as temporary.
4. Add a testing note indicating the replacement asset.