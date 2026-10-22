# Game Mechanics

Source of truth: which gameplay behavior is documented as original-game fact,
and which is this project's own design.

Status values:

- `Verified`: evidence exists and is cited here or in code (a wiki page, an
  unpacked file), ideally with a test that pins the value.
- `Designed`: independently designed for HoloJava; explicitly **not** a claim
  about HoloCure (strategic pivot, `Roadmap.md` §0 — the original logic sits in
  a compiled YYC build and is not recoverable).
- `Approximated`: numbers chosen without a citable source, flagged honestly in
  code.

## Current State

Everything below is implemented and tested: 41 suites under `tools/tests/`
describe the actual rules (see `docs/TEST_PLAN.md`). What this document adds
is provenance — where a number came from. No row claims behavioral parity with
HoloCure.

| Mechanic | Status | Evidence | Notes |
|---|---|---|---|
| Boss HP / ATK / EXP | Verified | Wiki stage pages, "Enemy Composition" tables, read 2026-10-20 | Every row flagged `documentedBaseStats = true` in `ContentCatalog.BOSSES`; bosses spawning on both a Normal and a Hard page have one row per appearance (`*_hard`); group bosses carry the mean of the members the wiki lists |
| Stage and boss schedule | Verified | Same stage pages (`documentedSource` per stage row) | 10:00 opener and 20:00 final gates; Stage 1 (Hard) also runs a second boss at 10:00 |
| Multi-boss system (single map) | Designed | — | Up to 3 simultaneous bosses via `spawnAdditionalBoss()`; each tracks own phase, HP, ATK; universal collision (melee, shots, AoE, homing) checks all active bosses |
| Boss phase transitions | Designed | — | Auto-transition at HP thresholds (25%/50%/75% per phase); HP ×1.5, ATK ×1.25 per phase, full heal, 1s white flash; max phases per boss configurable |
| Boss HP bar UI (enhanced) | Designed | — | 260×20 bar, gradient fill (red→orange→pulsing red), phase diamonds, phase flash, name+phase suffix, portrait placeholder, stacked multi-boss bars |
| Timer pause during popups | Designed | — | `elapsedMsAt(now)` subtracts suspension time during LEVEL_UP & PAUSED states; boss gates, wave spawns, minute scaling all respect suspension; timer resumes seamlessly |
| Stage clear-coin bonuses | Verified | Wiki Stage article, recorded in `data/wiki/modes.csv` | `StageDef.coinClear` (3000..50000) matches the documented `clearAmount` list |
| Shop upgrade prices | Verified | Wiki per-level price tables | `UpgradeDef.prices`; pinned by `CharacterShopTest` |
| EXP: level 1→2 threshold | Verified | Wiki Level Up page ("79 EXP") | Pinned in `RunWorld.requiredForLevel` |
| EXP curve beyond level 2 | Designed | — | The `power(4*level, 2.1)` shape in `requiredForLevel` is ours |
| Regular-enemy HP / ATK / EXP | Approximated | None — the wiki's Enemy page has no such columns | Flagged `documentedBaseStats = false`; see the comment above `ContentCatalog.ENEMIES` |
| Character stats (HP / ATK / speed / crit) | Designed | None | Flagged as ours; per-character defence is deliberately 0 because the original has no such stat — the evidence is spelled out in the `BASE_CHARACTERS` comment |
| Weapon / collab / item identity | Verified | `data/wiki/weapons.csv`, `collabs.csv`, `items.csv` — each row carries its `source_url` | Names, types, collab ingredients (weapon_a/weapon_b), descriptions |
| Weapon / collab / item numeric effects | Designed | — | Balancing lives in `ContentCatalog`; no parity claim |
| Movement, collision, AI, damage math | Designed | Independent design per `Roadmap.md` §0 | Hitboxes pinned to art by `RunWorldBossArtTest` |
| Mode objectives and completion rules | Verified | `data/wiki/modes.csv` (each row carries its `source_url`) | Objective text and completion rules are quoted from the Stage article |
| Stage 6 "Arena Hutan Sawit" and its cast | Designed | None — original content | WoWo/FufuFafa/Biji Sawit are ours; recorded in `docs/PROJECT_STATUS.md` |

## Rules

1. `Verified` requires a citation and, where feasible, a test that pins the
   value. Editing a pinned value means editing the pin deliberately —
   `BossStatsWikiTest` is the model.
2. `Designed` values must not be presented as recovered facts, in docs or in
   commit messages.
3. Values without a source keep `documentedBaseStats = false`; the flag may
   only flip together with the evidence.
4. New original content is labelled as original where it is defined (catalog
   comment) and where it is recorded (`docs/PROJECT_STATUS.md`).

| Multi-boss system (single map) | Designed | — | Up to 3 simultaneous bosses via `spawnAdditionalBoss()`; each tracks own phase, HP, ATK; universal collision (melee, shots, AoE, homing) checks all active bosses |
| Boss phase transitions | Designed | — | Auto-transition at HP thresholds (25%/50%/75% per phase); HP ×1.5, ATK ×1.25 per phase, full heal, 1s white flash; max phases per boss configurable |
| Boss HP bar UI (enhanced) | Designed | — | 260×20 bar, gradient fill (red→orange→pulsing red), phase diamonds, phase flash, name+phase suffix, portrait placeholder, stacked multi-boss bars |
| Timer pause during popups | Designed | — | `elapsedMsAt(now)` subtracts suspension time during LEVEL_UP & PAUSED states; boss gates, wave spawns, minute scaling all respect suspension; timer resumes seamlessly |
