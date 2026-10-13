# HoloJava Rebuild Project Audit

Date: 2026-10-13

## Audit Scope

This audit inspects the existing HoloJava project without modifying it. It covers local project structure, build/toolchain evidence, source sets, assets, existing prototype artifacts, documentation, and selected HoloCure wiki evidence. No gameplay code was implemented during this audit. No existing diagnostic or prototype artifact was overwritten.

## Evidence Sources

- HoloJava project root: `/home/antomay/Projects/HoloJava`
- Read-only HoloCure game: `/home/antomay/Projects/HoloCure`
- Read-only unpacked HoloCure resources: `/home/antomay/Projects/HoloCure_Unpacked`
- HoloCure wiki references and `config/assets.json`, `docs/`, `reports/`, `tools/`.

## Existing Source Sets

### `src/java-me-diagnostic/`

Purpose: minimal Phase 2 toolchain and LCDUI smoke test.

Characteristics:

- Contains `DiagnosticMIDlet.java`.
- Uses standard MIDP 2.0/LCDUI only.
- No gameplay systems, assets, vendor APIs, networking, audio, or persistence.
- Diagnostic-only; retain as baseline evidence.

### `src/java-me-first-slice/`

Purpose: initial playable vertical slice proof build.

Characteristics:

- Contains `SliceMIDlet.java` and `SliceGameCanvas.java`.
- Covers movement, one chasing enemy, contact damage, HP, temporary invulnerability, and Game Over.
- Uses two project-local proxy sprites.
- Prototype-only and explicitly not required as the full game's implementation foundation.

### `src/java-me-phase3/`

Purpose: broader gameplay build made after explicit approval.

Characteristics:

- Contains `GameMIDlet.java` and `HoloGameCanvas.java`.
- Implements movement, animation, enemies, automatic projectile, EXP pickup, level-up choices, capped spawning, scaling, HUD, Game Over, and run states.
- Uses verified local sprite sets from `assets/phase3/`.
- Prototype-only. This was a runtime proof and is not the required architectural foundation for the full game.

### `assets/prototype-first-slice/`

- Project-local copies of the proxy player and enemy.
- Diagnostic-quality assets only.
- Should not be treated as final art.

### `assets/phase3/`

- Project-local copies of the sprite sets used by the Phase 3 prototype.
- Assets include Gura idle/run frames, Nousagi enemy, projectile, and XP pickup frames.
- These copies are reversible Build/packaging evidence, not the long-term content pipeline.

## Build and Toolchain Assets

Reusable infrastructure is already present:

- CLDC 1.1 stub: `deps/java-me/cldcapi11-2.0.4.jar`
- MIDP 2.0 stub: `deps/java-me/midpapi20-2.0.4.jar`
- Compiler host: OpenJDK 21 with `--release 8`, producing class major version 52.
- Preverification: ProGuard 6.2.2 with `-microedition`.
- Build metadata: `config/assets.json`, `docs/ASSET_LOADER_CONTRACT.md`, `docs/ASSET_LOADER_TEST_PLAN.md`.
- Asset evidence inventory: `reports/asset_manifest.csv`, `reports/asset_manifest.json`, `reports/asset_manifest_summary.md`.

The diagnostic and prototype JAR/JAD files under `dist/` and `build/` should remain untouched as historical validation evidence.

## Documentation Inventory

The following documents are reusable references, not implementations:

- `Roadmap.md`
- `docs/PROJECT_STATUS.md`
- `docs/TECHNICAL_DECISIONS.md`
- `docs/JAVA_ME_COMPATIBILITY.md`
- `docs/TOOLCHAIN_READINESS.md`
- `docs/RECONSTRUCTION_SCOPE.md`
- `docs/SYSTEM_CATALOG.md`
- `docs/CONTENT_COVERAGE.md`
- `docs/ASSET_USAGE.md`
- `docs/ASSET_LOADER_CONTRACT.md`
- `docs/ASSET_LOADER_TEST_PLAN.md`
- `docs/GAMEPLAY_DESIGN.md`
- `docs/GAME_MECHANICS.md`
- `docs/TEST_PLAN.md`

Some process language still says Phase 3 was not started; subsequent user-approved prototype work has now modified that old statement. This audit deliberately leaves historical process descriptions intact while recording that the prototype work exists and is a separate evidence trail.

## Existing Prototype Classifications

Classification for each existing artifact:

| Artifact | Classification | Must Remain Untouched |
|---|---|---:|
| `src/java-me-diagnostic/*` | Diagnostic-only | Yes |
| `build/java-me-diagnostic/*` | Built diagnostic evidence | Yes |
| `dist/java-me-diagnostic/*` | Verified diagnostic JAR/JAD evidence | Yes |
| `src/java-me-first-slice/*` | Prototype-only | Yes |
| `dist/java-me-first-slice/*` | Prototype JAR/JAD evidence | Yes |
| `src/java-me-phase3/*` | Prototype-only | Yes |
| `dist/java-me-phase3/*` | Prototype JAR/JAD evidence | Yes |
| `assets/prototype-first-slice/*` | Prototype assets | Yes |
| `assets/phase3/*` | Prototype assets | Yes |
| `deps/java-me/*` | Reusable toolchain library | Yes |
| `config/assets.json` | Reusable asset metadata; needs future review | Avoid changing now |
| `docs/*` and `reports/*` | Reusable evidence | Modify only when needed |

No existing Java source was changed during this audit.

## Asset Baseline

From read-only `HoloCure_Unpacked`:

- 3,362 sprite directories.
- 8,121 extracted sprite frame files.
- 34,422 regular files overall.
- 8,124 unique hashes.
- 463,584,164 bytes.
- 33,494 image files with parseable dimensions.
- 380 audio files, including 365 WAV and 15 OGG.
- 839 collision masks.
- 29 rooms and 7 backgrounds.
- 524 game objects.
- 81,613 localized strings.
- 23 fonts.
- 68 sprite/logical groups before normalization, with dimension variance throughout the corpus.

These counts describe resources, not validated gameplay understanding.

## Candidate Asset Families

Representative local families relevant to a future implementation:

- Characters and character outfits: `spr_Gura_*`, `spr_Gura_O1_*`, `spr_Gura_Idle`, `spr_Gura_Run`, portraits (`spr_*_portrait`), and character selection UI assets. These are possible starting characters, but HoloCure Character sprite use, animation state, and direction need additional verification.
- Basic enemy types: `spr_NousagiEnemyA` through `spr_NousagiEnemyD`, `spr_NousagiA-D`, `spr_NousagiEnemyA-D`, and other stage-specific enemy sprites from local metadata.
- Weapon symbols: `spr_holoAxe`, `spr_GuraWeapon`, `spr_OllieChargeRifle1`, `spr_OllieChargeRifle2`, `spr_RobocoChargedShot`, `spr_GuraSharkSpecial`, `spr_GuraTridentThrust`, and many others. The mapping between local sprite names and wiki weapon entries is only partly inferable.
- Projectile and effect candidates: `spr_BulletTemp`, `spr_EXP`, `spr_fightpitPickUp_Attack`, `spr_Explosion`, `spr_HoloBeam`, `hud_pickupicon`.
- UI and fonts: `hud_*`, `ui_*`, and 23 fonts under `fonts/` (`audiowide`, `buffFont`, `jpFont`, etc.).
- Backgrounds: 196 `BG_...` directories and 7 backgrounds exported as separate assets.
- Sounds: 380 files including `sounds/sfx/snd_*` and `sounds/bgm/bgm_*`.
- Collision masks: 839 assets under `masks/`, buting no established runtime anchor schema.

Confidence is therefore uneven. Asset discovery is strong, animation semantics are weak, and gameplay role mapping remains an explicit design task.

## Wiki Gameplay Evidence

The wiki is a gameplay specification, not an implementation. Together with the Enemy page, the following mechanics have high-value documented facts:

- Enemy contact damage from the enemy's ATK on a cooldown.
- Enemy defeated counter and enemy lifecycle, including fixed lifetime behavior.
- Enemy drop chances for EXP, food, HoloCoins, anvils, and EXP magnet.
- Knockback rules and knockback resistance, with exact formulas/numbers.
- Spawn pool, spawn rate, spawn cooldown, maximum enemies, timed events, enemy level variants, and spawn patterns.
- Time and stats enemy scaling formulas in exact algebraic form.
- Boss/miniboss schedule: mini-bosses every 2 minutes, main bosses every 10 minutes.
- Weapon categories and lists: basic, main, collab, super collab; 6 weapon slots; maximum level 7; weight and descriptions.
- Item inventory limit: 6.
- EXP color tiers and exact value ranges.
- EXP merging and EXP magnet behavior.
- Collab and Super Item concepts.
- Item typings, levels, weights, descriptions, and unlock requirements.

The following areas still need more research or exact behavior extraction:

- Exact player collision box dimensions and targeted attack anchor behavior.
- Exact damage formula beyond weapon stats and buffs.
- Critical hit probability and critical multiplier details.
- Aim targeting rules, particularly for aimed versus automatic weapons.
- Skill cooldown/trigger and Special attack rules.
- Level-up offer probability, weighting, ban rules, and duplicate offer behavior.
- Anvil enhancement success and cost scaling beyond general descriptions.
- Stage timed events and map-specific enemy compositions, which must be fetched and audited if deeper parity is desired.
- Pickup collection behavior, XP magnet radius, pickup momentum, and merging details.
- Score and `Anvil` behavior.
- Banking/idle mechanics, if any, for a portable clone.

## Key Findings

1. The prototype assets are sufficient for runtime proofs, but the full asset atlas of HoloCure is large and variable in scale.
2. MIDP 2.0 resource choice is constrained to unpacked PNG-`,` WAV/OGG-like local files; audio support is platform-dependent and was not verified.
3. The widget wiki contains substantial data, but it often describes effects rather than exact implementation formulas.
4. A clean architecture must keep content definitions separate from engine code; otherwise the 47-character and 94-weapon scope will require core rewrites.
5. The prototype source sets demonstrate feasibility but are treated as evidence, not as the required foundation. The clean rebuild is isolated under `src/java-me-fullgame`.
6. The repository currently has no test framework; acceptance evidence is build, JAR validation, and manual J2ME Loader runs.

## Current Implementation Step

The audit and roadmap have now been used to implement `src/java-me-fullgame`. The dedicated artifact is `dist/java-me-fullgame/HoloJavaFullGame.jar`. Static build and catalog verification pass; J2ME Loader gameplay validation and the remaining approximation/parity work are the next gates.
