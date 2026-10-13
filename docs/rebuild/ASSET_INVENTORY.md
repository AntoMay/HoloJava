# HoloJava Rebuild Asset Inventory

Source: `/home/antomay/Projects/HoloCure_Unpacked` (read-only). No assets were copied during this audit.

## Read-Only Evidence

The unpacked extraction has 3,362 sprite directories and 8,121 frame files across 34,422 regular files. Counts below are inventory counts, not gameplay validation.

## Baseline Inventory

| Media class | Source count | Inventory status | MIDP 2.0 compatibility concern |
|---|---:|---|---|
| Sprite directories | 3,362 | Identified only | Must be sampled and normalized before use |
| Sprite frame files | 8,121 | Identified; selected prototype sets verified | PNG transparency usually supported, but large RGBA assets consume heap |
| Unique hashes | 8,124 | Identified | Deduplication opportunities are not yet validated |
| Image files with dimensions | 33,494 | Identified | Many sprites are much larger than 320x240 |
| Audio files | 380 (365 WAV, 15 OGG) | Metadata verified for selected candidates | Player support depends on J2ME Loader and APIs; format is not guaranteed |
| Collision masks | 839 | Count only | Anchor and scale semantics unknown |
| Rooms/backgrounds | 29 rooms, 7 backgrounds, plus 196 `BG_*` dirs | Identified | Stage renders are not direct runtime backgrounds |
| Fonts | 23 | Identified | Font glyphs can be used as image sets but memory needs validation |
| Game objects | 524 | Identified by name | Native YYC implementation is not available for behavior extraction |
| Strings | 81,613 | Identified | Localization can be deferred; only targeted strings needed |
| Embedded images/textures | 15 images, 26 textures | Identified | Not directly mapped to runtime sprite keys |

## Character and Outfit Assets

Representative local families include:

- `spr_Gura_Idle` (3 frames), `spr_Gura_Run` (6 frames), and multiple `spr_Gura_O1_*` to `spr_Gura_O4_*` animation families.
- `spr_<Character>_Idle` and `spr_<Character>_Run`, plus portrait and selection assets.
- Numeric outfit variants described in the Character wiki (up to 4 alternate outfits for some characters).

Constraints:

- The Number of Characters and outfits is large: 47 playable characters plus variations.
- Local animation frame counts vary, and several frame-to-size pairs are nonuniform.
- Directional mirroring is not available from a simple MIDP `drawImage` call; it must be represented by separate frames or render code.
- Character repository and sprite rect constants can be data-driven later, but initial implementation must select a small set.

Confidence: medium for existence and dimensions; low for animation sequencing and gameplay mapping.

## Enemy Assets

Representative local families include:

- `spr_NousagiEnemyA` through `D` and `spr_NousagiA-D`.
- `spr_NousagiEnemyA`, `B`, `C`, and `D` have three frames with dimensions near 22-25 by 21-24.
- `spr_4thTakoA`, `spr_4thTakoB`, `spr_Enemy*`, and many stage-specific combinations.
- Boss and mini-boss animations use larger frame sets, for example `spr_GuraSharkSpecial` (18 frames) and trident/hat effects.

Constraints:

- Local sprite names suggest enemy families but do not independently establish enemy stats, AI, role, or spawn conditions.
- Boss/miniboss semantics are documented by wiki but require separate asset roles and scaling rules.
- Large animations should not be loaded all at once on a CLDC/MIDP heap.

Confidence: medium for sprite availability; low for enemy-facing animations and role mapping without wiki stage data.

## Weapon and Projectile Assets

There are 94 weapons documented, but only a small number of identifiable local weapon sprite families:

- `spr_holoAxe`, `spr_HoloBombIcon`, `spr_gacha_holologo`.
- `spr_GuraWeapon`, `spr_GuraSharkBite`, `spr_GuraSharkSpecial`, `spr_GuraTridentThrust`, `spr_GuraMaxedWeapon`.
- `spr_OllieChargeRifle1`, `spr_OllieChargeRifle2`, `spr_RobocoChargedShot`.
- `spr_HoloBeam`, `spr_CocoWeapon`, `spr_GeoCubeShards`, and text icons under `hud_*`.

Constraints:

- Local sprites cannot represent all 94 weapon attacks, and weapon sprite semantics are not encoded in filenames.
- Java ME cannot support all weapon behaviors with one generic class unless each behavior is represented as explicit component data.
- Audio, color cycling, and particle effects are not required for a first content slice.

Confidence: low to medium; must be resolved per weapon during implementation.

## Drop and Pickup Assets

Verified local asset groups include:

- `sprites/spr_EXP/spr_EXP_0.png` through `spr_EXP_11.png`, 12 frames, 12x13.
- `sprites/spr_fightpitPickUp_Attack/spr_fightpitPickUp_Attack_0.png` through `_5.png`, six frames, 21x21.
- `sprites/spr_Explosion/spr_Explosion_0.png` through `_8.png`, nine frames, mixed sizes from 24x24 to 114x118.
- `sprites/hud_pickupicon/hud_pickupicon_0.png`, 16x16.
- `sprites/ui_digit_white/ui_digit_white_0.png` through `_9.png`, 10 frames, 5x7.
- `sprites/ui_level_header_white/ui_level_header_white_0.png`, 20x9.
- `sprites/spr_BulletTemp/spr_BulletTemp_0.png`, 7x7.

Constraints:

- `effect.explosion` has nonuniform frame sizes and should either preserve original dimensions or be excluded from a uniform animation contract.
- Many pickup sprites are useful but their gameplay role must be verified.

Confidence: high for local availability and dimensions; medium for animation semantics.

## UI, Background, and Font Assets

UI assets include many `hud_*`, `menu_*`, and `ui_*` folders. Early documented examples include digit frames, level headers, and small pickups. Background assets include 196 `BG_*` directories plus 7 extracted background images. Fonts include 23 folders under `fonts/`.

Constraints:

- Background and decoration assets are large relative to 320x240 and may need filtering or replacement.
- The MIDP first implementation should render text with `Canvas`/`Font` or use tiny local glyph sets rather than loading large fonts.
- Menu, settings, level-up, and HUD requirements are substantial and should drive the asset catalog separately from gameplay entity assets.

Confidence: medium for inventory, low for required post-processing.

## Audio Assets

Candidate local audio groups include main game sfx and music from:

- `sounds/sfx: 365 WAV files.
- `sounds/bgm`: 15 OGG files.

Selected metadata already exists for five candidates (bullet, hit, EXP collect, explosion, one BGM). Audio is platform-dependent on CLDC/MIDP and may not work on all J2ME Loader devices. Audio must remain optional with a documented fallback.

## Local Asset Reuse Boundary

Although prototypes have already copied local assets under `assets/prototype-first-slice` and `assets/phase3`, those copies are historical validation inputs only. A clean rebuild should:

1. Inspect this inventory before adding any new load.
2. Define exact logical content keys.
3. Select a much smaller initial content set.
4. Create a documented project-local copying or processing step with checksum and rationale.
5. Keep original HoloCure files read-only at all times.

No additional asset copying should happen until Milestone M0 approves the content pipeline.

## Full-Game Snapshot Addendum

The rebuilt runtime snapshot is documented in `docs/rebuild/ASSET_MAPPING.md` and `reports/fullgame_asset_manifest.csv`.

- 166 project-local PNG files are packaged under `assets/fullgame/`.
- Startup loads character, projectile, pickup, and UI assets.
- Enemy and boss frame arrays load per stage and old stage arrays are released on a new run.
- Stage tiles load on first use.
- `office.png` and `fantasy.png` are explicit local normalizations of large source art to 128x128 tiles.
- Current full JAR is 312,521 bytes compressed; this is not a decoded heap measurement.
- Estimated decoded PNG storage for the complete source snapshot is approximately 741 KB, while the stage-scoped runtime keeps only the selected enemy/boss families plus common assets loaded.
- A 320x240 framebuffer and device runtime overhead still require J2ME Loader memory testing.
