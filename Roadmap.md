# HoloJava Master Roadmap

Version: `0.3`  
Created: `2026-10-13`  
Status: refreshed 2026-10-22 — Phases 0-6 complete (Phase 6 retired into the recurring audit cycle), Phase 7 active, Phase 8 not started  
Source of truth for scope, phase order, priorities, and project status.

---

## 0. Strategic Pivot

HoloJava is no longer a source-equivalent reconstruction of HoloCure. The original GameMaker project was compiled with YYC, and its algorithmic logic is not recoverable from the extracted files. Full YYC decompilation is deferred and treated as optional.

The objective is now an independently designed Java 2D survival roguelite that uses selected local HoloCure assets as visual and audio resources where suitable. The original HoloCure executable is a reference, not a required source of recoverable algorithms.

Key principles:

1. Do not attempt exhaustive YYC decompilation.
2. Design gameplay systems independently.
3. Reuse suitable assets by reference rather than duplicating the whole extraction.
4. Keep original extracted assets read-only.
5. Target a 320×240 internal resolution.
6. Use Java as the implementation language, targeting Java ME/J2ME-compatible bytecode under the provisional CLDC 1.1 + MIDP 2.0 baseline. Linux is the development/build environment only.
7. Keep simulation, rendering, input, entities, combat, progression, and configuration separate.
8. Use configurable gameplay parameters.
9. Clearly distinguish original-game facts, asset metadata, design decisions, and unverified assumptions.

## 1. Target Game Concept

Initial gameplay loop:

1. Start a run in a simple arena.
2. Control one character using keyboard input.
3. Spawn enemies over time.
4. Move enemies toward the player using simple, independently designed AI.
5. Provide an initial automatic or simple player attack.
6. Apply damage and remove defeated enemies.
7. Drop XP items and allow the player to collect them.
8. Increase player level and present a small upgrade selection.
9. Increase encounter difficulty over time.
10. End the run when the player is defeated.

These are new design requirements. They are not claims about exact HoloCure behavior unless separately verified and recorded in `GAME_MECHANICS.md`.

### Original Stage: Arena Hutan Sawit

Added 2026-10-17 as original content rather than reconstruction. `stage6` carries enemy Biji Sawit
(`AI_CHARGER`) and boss WoWo (`AI_SHOOTER`), uses a generated `palm_forest` tile, and unlocks on
clearing Fantasy Island. Its enemy, boss, tile, and all combat values are this project's own
design and are recorded in `docs/PROJECT_STATUS.md`. No part of it is evidence about the original
game, and none of its numbers are presented as verified. Its generated art is inventoried like
any packaged asset in `reports/fullgame_asset_manifest.csv` (the extraction-era coverage notes
are archived at `docs/archive/CONTENT_COVERAGE.md`).

## 2. Document Authority

- `Roadmap.md` is the primary source of truth for scope, phase order, priorities, and project status.
- `docs/TECHNICAL_DECISIONS.md` is the primary source of truth for architecture decisions.
- `docs/JAVA_ME_COMPATIBILITY.md` is the primary source of truth for Java ME/J2ME compatibility constraints.
- `docs/TOOLCHAIN_READINESS.md` is the primary source of truth for build-environment and toolchain status.
- `docs/REVERSE_ENGINEERING_MAP.md` is the primary source of truth for extracted evidence.
- `docs/GAME_MECHANICS.md` is the primary source of truth for which gameplay figures are source-verified and which are this project's design.
- `docs/GAMEPLAY_DESIGN.md` is the primary source of truth for our independent design.
- `docs/RECONSTRUCTION_SCOPE.md` is the primary source of truth for included and excluded reconstruction scope.
- `docs/archive/SYSTEM_CATALOG.md` (archived 2026-10-20) keeps the extraction-phase evidence inventory; current system status lives in `docs/PROJECT_STATUS.md`.
- `reports/fullgame_asset_manifest.csv` is the inventory of packaged assets, re-checked on every build; extraction-phase coverage notes are archived at `docs/archive/CONTENT_COVERAGE.md`.
- `docs/ASSET_USAGE.md` is the primary source of truth for asset references, metadata, and licensing notes.
- `docs/TEST_PLAN.md` is the primary source of truth for test strategy.
- `README.md` is the entry point for building, testing, and playing.
- `docs/CHOOSE_IDOL_LAYOUT.md` is the primary source of truth for the Choose Your Idol screen layout.
- `docs/PROJECT_STATUS.md` is the primary source of truth for current status and next action.

## 3. Initial Audit Summary

### Environment

- HoloJava project directory existed before this pivot.
- Git repository: not present. Existing project files were preserved.
- OS: Debian GNU/Linux 13.
- Java/JDK: installed and verified (OpenJDK 21 provided the diagnostic build host).
- Maven/Gradle/Ant: not installed.
- UndertaleModTool CLI: present at `/home/antomay/Projects/_tools/UTMT_CLI`.

### Extraction Artifacts

Original game:

- `/home/antomay/Projects/HoloCure`

Extraction output:

- `/home/antomay/Projects/HoloCure_Unpacked`

Verified extraction counts:

- 34,422 regular files on the last full manifest scan.
- 3,362 sprite directories.
- 380 audio files.
- 48 shaders.
- 23 fonts.
- 524 GameMaker objects.
- 29 rooms.
- 8,166 texture page items.
- 81,613 strings.
- 27,501 `gml_Script_*` native symbols.

Note: the current manifest was being rescanned for audio duration during this pivot. The latest complete scan before that rescan remains useful for counts already documented.

### Architecture Decision

- Provisional runtime profile candidate: CLDC 1.1 + MIDP 2.0; desktop game engines and Linux are not runtime targets.
- **Simplified planning direction (working assumptions):** primary target is J2ME Loader on Android; standard MIDP 2.0/LCDUI APIs are assumed usable on Nokia compatibility targets until testing. 320×240 internal resolution, aspect-preserving scaling, buttons-first input, pointer/audio optional, and no Nokia vendor APIs without a concrete requirement.
- Internal resolution: 320×240.
- Final artifact target: runnable `.jar`.
- `GAMEPLAY_DESIGN.md` and `ASSET_USAGE.md` must be read before a firmware or asset integration implementation begins.

## 4. Permanent Project Rules

1. Read `Roadmap.md` before any task and identify the active phase.
2. Every task needs an objective, reason, dependencies, and acceptance criteria.
3. Do not start a later phase before the active phase gate is complete unless deviation is documented.
4. Do not conduct exhaustive YYC reverse engineering as a required implementation gate.
5. Do not invent original-game formulas.
6. Do not claim assets are legal to redistribute unless that has been verified.
7. Mark placeholder and temporary values explicitly.
8. Keep build runnable at every implementation milestone.
9. Do not initialize or alter repositories without inspecting status.
10. Do not delete existing work or artifacts without explicit approval.
11. Keep original extracted data read-only.

## 5. Planned Directory Structure

```text
HoloJava/
  Roadmap.md
  README.md
  docs/
    PROJECT_STATUS.md
    TECHNICAL_DECISIONS.md
    REVERSE_ENGINEERING_MAP.md
    GAME_MECHANICS.md
    GAMEPLAY_DESIGN.md
    ASSET_USAGE.md
    TEST_PLAN.md
    CHOOSE_IDOL_LAYOUT.md        # Choose Your Idol screen geometry (320x240)
    CHOOSE_IDOL_LAYOUT_PARTS.md  # Generated layout part sheets
  reports/
  tools/
  src/                 # Added later in implementation phases
  assets/              # Local-only configuration and references; not bulk asset storage
  dist/                # Created during packaging
```

Planning-era sketch (2026-10-13). `src/` and build configuration were intentionally not created until the implementation gates were reached; those gates have since passed, and the tree as built (with `data/`, `deps/`, `docs/archive/` and more) is documented in `README.md` § Layout.

## 6. Revised Phase Roadmap

### Status Vocabulary (applies to system, content, and phase records)

- `Identified`: discovered or inferred from evidence; not implemented.
- `Planned`: included in HoloJava design; implementation is not present.
- `Implemented`: Java implementation exists; implementation alone does not imply testing.
- `Tested`: implementation has documented, reproducible test evidence.
- `Deferred`: intentionally postponed or excluded for a documented reason.
- `Unresolved`: evidence, meaning, ownership, or priority is insufficient.
- `Not in scope`: explicitly excluded by `docs/RECONSTRUCTION_SCOPE.md` or another accepted decision.

Technical asset metadata validation records format and dimensions only. It does not establish a candidate asset's intended original gameplay meaning.

### Broad Reconstruction Position (Phase 0-8)

HoloJava's long-term target is broad reconstruction of identifiable HoloCure systems, content, and presentation. HoloCure remains the local asset and presentation reference. Gameplay logic, AI, balance, progression, and stage systems will be independently designed and implemented in Java. HoloJava does not claim exact behavioral, formula, or implementation parity with the original GameMaker YYC build. Symbol names and extracted metadata are evidence, not recovered source code. Exhaustive reverse engineering remains optional and deferred rather than a prerequisite.

### Phase 0 — Project Audit and Planning

Status: complete.

Entry condition:

- Project directory exists.
- Initial extraction reports are available.
- No game implementation is required before this gate.

Deliverables:

- Initial read-only audit.
- `Roadmap.md`.
- Essential companion documents only.

Acceptance criteria:

- Audit matches actual extraction state.
- Existing user work is preserved.
- No gameplay code or build tooling is introduced merely by planning.
- Full YYC reverse engineering is not a prerequisite for implementation.

### Phase 1 — Asset and System Discovery

Status: complete for evidence inventory.

Entry condition:

- Phase 0 complete.
- Extraction manifest and `data.win` metadata available.

Deliverables:

- `reports/asset_manifest.*`
- `docs/ASSET_USAGE.md`
- Verified asset paths, dimensions, formats, and durations
- Candidate asset shortlist for the first playable slice.
- System evidence references from the asset, symbol, object, room, and string inventories.

Acceptance criteria:

- Candidate assets have verified relative paths and metadata.
- Missing or ambiguous assets are recorded.
- Asset redistribution restrictions are documented.
- No assets are duplicated into the project as implementation work.
- No unverified mechanism is labelled as reconstructed.

### Phase 2 — Architecture and Toolchain Readiness

Status: complete. Diagnostic toolchain validation verified.

Verified evidence as of 2026-10-13:

- OpenJDK 21, ProGuard 6.2.2, and pinned CLDC 1.1/MIDP 2.0 API stubs completed a diagnostic build.
- ProGuard preverification with `-microedition` succeeded.
- A 3,153-byte JAR and matching JAD were packaged and their descriptor fields agreed.
- J2ME Loader runtime behavior passed for MIDlet launch, key input, and Canvas dimensions.
- No Nokia, audio, pointer, multi-resolution, or long-term-stability claim is made from this test.
- Phase 3 remains not started and requires separate explicit approval.

Entry condition:

- Phase 1 complete.
- Candidate assets, symbols, and environment constraints are documented.

Deliverables:

- Documented Java ME compatibility, MIDlet lifecycle, and rendering constraints (not a desktop architecture).
- Simple, bounded Phase 2 planning: primary J2ME Loader target, MIDP 2.0/LCDUI workset, three blockers only.
- Build requirements and setup plan without installation.
- Fixed 320×240 rendering and update strategy.
- State model and configuration model.
- Asset-loading contract and asset-key resolution rules in `docs/ASSET_LOADER_CONTRACT.md`.
- Asset loader test outline in `docs/ASSET_LOADER_TEST_PLAN.md`.
- Asset catalog in `config/assets.json`.
- Test strategy aligned with `docs/TEST_PLAN.md`.
- Bounded system backlog only; no expanded per-device compatibility research.

Acceptance criteria:

- Internal resolution of 320×240 is explicit and testable.
- Simulation and rendering remain logically separate.
- Gameplay parameters remain configurable rather than hard-coded.
- No Java, Gradle, or libGDX installation occurs before explicit authorization.
- A single next implementation target remains unambiguous.

### Phase 2 Closure Record

Phase 2 diagnostic toolchain validation is complete. Verified scope is limited to a minimal MIDlet using MIDP 2.0/LCDUI APIs on J2ME Loader, validated for compilation, ProGuard 6.2.2 preverification, JAR/JAD packaging, launch, key input, and the tested Canvas dimensions. Does not claim Nokia support, audio support, pointer behavior, broader scaling behavior, production runtime robustness, asset licensing clearance, or game implementation. Phase 3 remains unauthorized.

### Phase 3 — First Playable Vertical Slice

Status: complete. Implemented 2026-10-13 as the clean full-game rebuild under `src/java-me-fullgame`; the acceptance criteria below are exercised daily by the test suites. The Gradle/libGDX entry assumption was superseded by the Java ME / CLDC pivot (§10, TD-003).

Entry condition:

- Phase 2 complete.
- Approved JDK, Gradle, and libGDX toolchain available.
- Empty Java/libGDX smoke application runs.

Deliverables:

- Player movement.
- Simple arena.
- One enemy.
- Simple attack or collision-based combat.
- Damage application.
- XP drop and pickup.
- Level-up trigger and upgrade selection.
- Difficulty ramp.
- Game-over state.

Acceptance criteria:

- A run can be started and lost.
- Player movement is visible and controllable.
- At least one enemy spawns and moves toward or attacks the player.
- Damage can defeat an enemy.
- Defeated enemies produce XP.
- Player level can increase and enter a deterministic upgrade state.
- Game-over transition is testable without rendering.

### Phase 4 — Core Gameplay Expansion

Status: complete. Weapons and collabs, items, skills and specials, the character shop's passives, difficulty progression and the upgrade pool all exist, each with suites under `tools/tests/`.

Entry condition:

- Phase 3 vertical slice passes its acceptance criteria.
- Candidate assets and design assumptions are accepted or replaced.

Deliverables:

- Additional enemies.
- Additional weapons or attack variants.
- Upgrade pool expansion.
- Items or pickups.
- Difficulty progression rules.
- Additional object and resource catalog entries.

Acceptance criteria:

- Expanded content uses the same state model.
- New gameplay is documented in `GAMEPLAY_DESIGN.md`.
- Balance values remain marked temporary where unvalidated.
- Tests cover new transitions.
- Expansion does not invalidate isolated module APIs.

### Phase 5 — Broad Content and Asset Integration

Status: complete. The five reference stages and their four Hard variants, time attack and the original stage are wired to packaged art, per-stage BGM, UI and fallbacks; `reports/fullgame_asset_manifest.csv` inventories the set.

Entry condition:

- Phase 4 expansion boundary is documented.
- Local asset paths remain configured through the asset loader contract.

Deliverables:

- Integration of additional verified assets.
- Stage-specific backgrounds, sounds, fonts, and effects.
- UI and text asset integration.
- Placeholder replacement strategy for unresolved assets.

Acceptance criteria:

- Asset selection is recorded in `reports/fullgame_asset_manifest.csv`.
- Missing or unresolved assets are not treated as successfully loaded gameplay assets.
- Assets remain referenced locally until redistribution is reviewed.
- No unverified animation semantics are documented as recovered.
- Vertical-slice gameplay is not blocked by optional content.

### Phase 6 — Content and System Coverage Audit

Status: complete, in a different shape than planned. Coverage settled into a recurring audit cycle (`reports/`), the `SYSTEM_CATALOG.md`/`CONTENT_COVERAGE.md` deliverables were archived on 2026-10-20 (see §12), and the status-plus-denominator job is done by `reports/fullgame_asset_manifest.csv` and the test suites.

Entry condition:

- At least Phase 3 gameplay and Phase 5 candidate content are available for audit.

Deliverables:

- Completion of `docs/SYSTEM_CATALOG.md`.
- Completion of `docs/CONTENT_COVERAGE.md`.
- Explicit counts by status and denominator.
- Deferred and unresolved item lists.
- Updated known limitations.

Acceptance criteria:

- Every identified asset or system has a status label.
- A feature is not marked `Implemented` solely because it exists in source code.
- A feature is not marked `Tested` without documented evidence.
- Counts distinguish identified, planned, implemented, tested, deferred, and unresolved categories.
- Coverage reporting states its denominator and unresolved count.

### Phase 7 — Testing, Balancing, and Polish

Status: active. 41 suites (~4,300 checks) run green on every commit along with the CLDC bytecode gate; balancing and polish continue through audit passes (latest 2026-10-20). The deliverables below are largely in place except device-side testing, which no suite covers.

Entry condition:

- Phase 5 integration baseline is playable or equivalently testable.
- Phase 6 initial coverage taxonomy is populated.

Deliverables:

- Automated tests for simulation contracts.
- Asset loader contract tests where feasible.
- Performance observations at 320×240.
- Visual and audio polish notes.
- Balancing notes with temporary-value markers.

Acceptance criteria:

- No gameplay state depends on renderer implementation details.
- Missing or unselected assets fail gracefully.
- Performance notes are recorded separately from gameplay status.

### Phase 8 — Distribution Readiness

Status: not started.

Entry condition:

- Phase 7 validation baseline is available.

Deliverables:

- Licensing review.
- Asset permission review or placeholder substitution.
- Release notes.
- Known issues list.
- Packaging verification.

Acceptance criteria:

- Public distribution is not attempted without documented asset permissions.
- Final artifact launches on a clean JVM.
- Scope and limitations are documented.

## 7. First Vertical Slice Requirements

The first playable slice must include:

1. Window with fixed 320×240 logical resolution.
2. One controllable avatar.
3. One static or simple arena background.
4. One basic enemy.
5. One basic projectile or automatic attack.
6. Collision and damage application.
7. XP pickup and collection.
8. Level-up trigger.
9. Basic upgrade choice state.
10. Difficulty ramp.
11. Game-over state.
12. Visual placeholders for missing assets.
13. Unit and smoke tests for state transitions.

The following are explicitly deferred:

- Full HoloCure character roster.
- Exact weapon evolution recipes.
- Exact original enemy AI.
- Save file compatibility.
- Online leaderboards.
- Finish all asset conversions.

## 8. Dependency and Execution Order

1. Phase 2 architecture, asset, broad-reconstruction, and toolchain planning.
2. Phase 3 Java baseline and vertical-slice gameplay.
3. Phase 4 core gameplay expansion.
4. Phase 5 broad content and asset integration.
5. Phase 6 content and system coverage audit.
6. Phase 7 testing, balancing, and polish.
7. Phase 8 distribution readiness.

Phase gates and approval requirements remain active. Dependencies must be recorded in `PROJECT_STATUS.md` before this order is changed.

## 9. Risks and Mitigation

| Risk | Mitigation |
|---|---|
| Java/JDK not installed | Document setup only; do not install without approval |
| Assets may not be redistributable | Keep assets local and document uncertainty |
| Extracted sprite dimensions mismatch intended game scale | Use verified metadata and plan scaling |
| Gameplay balancing unknown | Use external JSON/YAML config with marked temporary values |
| Toolchain complexity | Minimize dependencies and document each choice |
| Scope creep back to full reverse engineering | `GAMEPLAY_DESIGN.md` and roadmap phase gates block it |

## 10. Technical Decisions Summary

| ID | Decision | Rationale | Status |
|---|---|---|---|
| TD-001 | Java 21 LTS | Modern LTS | Build-host provisional only |
| TD-002 | Gradle wrapper | Reproducible builds | Provisional if Java SE build host is approved |
| TD-003 | libGDX | Viewport, assets, audio, input | Superseded by Java ME target |
| TD-004 | Fixed 320×240 resolution | Explicit requirement | Accepted |
| TD-005 | Simulation separated from rendering | Testability | Accepted |
| TD-006 | Assets referenced locally, not committed by default | Licensing caution | Accepted |
| TD-007 | Configurable gameplay parameters | Independent balancing | Planned |
| TD-008 | Exhaustive YYC reverse engineering deferred | Avoid unrecoverable source dependency | Accepted |

## 11. Unknowns and Assumptions

Unknown:

- Exact original game behavior.
- Exact damage, XP, and timing constants.
- Asset licensing terms.
- Final target platform packaging details.

Assumptions:

- Candidate placeholder assets may be used only in local development until licensing is clear.
- Independent gameplay constants can produce an enjoyable first slice without matching original HoloCure behavior.
- A 320×240 logical resolution is sufficient and desirable.

## 12. Change Log

| Date | Change | Reason |
|---|---|---|
| 2026-10-13 | Initial roadmap created | First audit |
| 2026-10-13 | Phase 1 extraction documentation completed | Asset and symbol reports generated |
| 2026-10-13 | Strategic pivot to original Java game | YYC source is not recoverable; objective changed to playable independent game |
| 2026-10-13 | Roadmap phase sequence revised to required Phases 0-6 | Matching mandatory strategic pivot and concrete phase gates |
| 2026-10-13 | Roadmap expanded to Phases 0-8 for broad reconstruction documentation | Approved documentation milestone; no implementation started |
| 2026-10-13 | Created `RECONSTRUCTION_SCOPE.md`, `SYSTEM_CATALOG.md`, and `CONTENT_COVERAGE.md` | Separate broad-reconstruction scope, system, and content tracking |
| 2026-10-13 | Compatibility target and toolchain review changed to Java ME / J2ME | Desktop runtime direction is superseded by compatibility evidence; no implementation occurred |
| 2026-10-13 | Clean full-game rebuild implemented under `src/java-me-fullgame` | Explicitly authorized implementation milestone; prior prototypes preserved |
| 2026-10-17 | Added original stage `stage6` "Arena Hutan Sawit" with enemy Biji Sawit and boss WoWo, gated behind Fantasy Island | User-requested original content; combat values and art are this project's own design, not recovered from the reference game |
| 2026-10-17 | Widened the RMS completion map from one byte to two per character (stage orders 0-15) | `stage6` needs order 10, and `stage4h` already sat at order 8 where the single-byte layout silently refused to record it; existing saves still decode because the new high bytes read as zero |
| 2026-10-17 | Opened `stage6` to `unlockRequirement` 99 (always available) | Requested so the stage could be reached without clearing Fantasy Island first, whose final boss gates at 20:00. The intended gate (4) is recorded in a comment on the `stage6` line and is one literal to restore |
| 2026-10-17 | Added `tools/build_fullgame_desktop.sh` after two runtime blockers | The preverified JAR cannot be loaded by a desktop JVM: ProGuard 6.2.2 emits a `full_frame` with zero locals, which is rejected before any MIDlet code runs. Separately, FreeJ2ME-Plus places `-Dfile.encoding` after `-jar`, so it restarts forever on an encoding mismatch. Both are recorded in `docs/PROJECT_STATUS.md` |
| 2026-10-17 | Fixed the level-up offer mix and softened Arena Hutan Sawit's pressure | `chooseOfferType` tested `r < 20` on `rand(20)`, always true, which pinned two slots to a duplicate skill offer and made stat upgrades near-unreachable. Stat upgrades now take 25% of rolls; the stage's enemy ATK, cap and spawn rate were lowered to late-stage-free values |
| 2026-10-17 | Nerfed Biji Sawit (ATK 4→3, speed 0.55→0.48, dash 7→5 px/tick) and gave it a circling wind-up before it darts (`AI_ORBIT_CHARGER`) | Requested so the swarm reads as a milling crowd: it now circles the player for 2-3 random laps at a 55 px ring, direction re-rolled per approach, before committing the dash |
| 2026-10-17 | Arena Hutan Sawit background is now the reference scene | `StageDef` gained `mapCols`/`mapRows`; a composed 4x4 grid of 80x60 parts is drawn at fixed positions instead of one repeating tile, because the reference is a single composed picture and cannot tile |
| 2026-10-17 | Fixed the RMS write path: fall back to `addRecord` when `setRecord` reports an unknown id | `setRecord` only replaces a record whose id the store issued, so on a fresh install every save failed. Nothing the game stored had ever been stored: balances, stage unlocks, the selected character and shop passives |
| 2026-10-17 | Added a character shop with eight permanent passives (0..10 levels each, per character), and made skills stackable to 5 | The skill was a one-shot boolean, so taking the skill slot again was a wasted level. Passives fill `defensePercent`, which `damagePlayer` read but nothing set, and add the game's only coin-rate source |
| 2026-10-18 | Menu, HUD and BGM stream: HoloCure-style main-menu flow, per-stage MIDI BGM, gameplay HUD rebuild, FufuFafa and 19 Kucing added, public build ships zero starting coins | Play-test feedback; the day is recorded in `docs/PROJECT_STATUS.md` |
| 2026-10-19 | Combat correctness and Choose-Your-Idol rebuild: character ATK double-application fixed and damage values restored, weapon hitboxes follow the sprite, idol screen rebuilt; `dist/` split into DevBuild/Public | Play-test findings; detail lives in the git log |
| 2026-10-20 | Full-project audit; fixes 1-6 (CLDC-only collab parsing, Hold offer charge, Growth/Skill Up wiring, one-shot scripts dropped, CLDC bytecode gate proven per build, music/coin write throttling) | Audit order agreed with the project owner; each commit body carries the detail |
| 2026-10-20 | Boss HP/ATK/EXP replaced with the wiki's per-stage figures; `*_hard` rows added for the Hard-page appearances; boss-HP ceiling raised to 400000 | The old table had EXP misread into HP and stages stepping backwards; provenance now recorded in `docs/GAME_MECHANICS.md` |
| 2026-10-20 | README added; `TEST_PLAN.md`/`GAME_MECHANICS.md` rewritten; `SYSTEM_CATALOG.md`/`CONTENT_COVERAGE.md` archived to `docs/archive/`; phase statuses in this file caught up | The status documents still described the 2026-10-13 research phase, not the implemented game |
| 2026-10-20 | Research manifests (`asset_manifest.json`, `asset_manifest.csv`, `symbol_classification.csv`) untracked as regenerable outputs | ~33 MB of machine-generated data in git; regenerate with `tools/build_asset_manifest.py` and `tools/classify_symbols.py` |
| 2026-10-22 | Boss HP bar overhaul: wider, gradient fill, phase indicators, multi-boss stacking | Boss UI polish + multi-boss support for single map
| 2026-10-22 | Multi-boss system (max 3): spawnAdditionalBoss, phase transitions, universal collision | Single-map multi-boss encounters
| 2026-10-22 | Timer pauses during LEVEL UP & pause popups | suspension ledger freezes elapsed time during popups
| 2026-10-22 | freej2me-plus fixes: number row→numpad, PC numpad direct KEY_NUM, super(false) | Emulator input fixes for special key (9)
| 2026-10-22 | Notifications: removed Level-up/Special-ready popups; Boss notification text-only | Clean UI, no box/border
| 2026-10-22 | Special skill visual effects: 5 character-specific procedural animations | No new assets, procedural only

## 13. Current Status and Next Step

Refreshed 2026-10-20. The game is playable: `sh tools/build_all.sh` produces the DevBuild and Public jars, the desktop jar runs in FreeJ2ME-Plus (the build prints the exact command), and the mobile jar sideloads for J2ME Loader or a phone. 41 suites (~4,300 checks) plus the CLDC bytecode gate run green on every commit. Content in place: the five reference stages and their four Hard variants, time attack, the original stage6, five idols, weapons/collabs/items with wiki-sourced identity and shop prices, wiki-sourced boss figures, save, shop, and per-stage BGM.

Recommended next action:

Work the follow-up list recorded in `docs/PROJECT_STATUS.md` (frame-allocation churn, the `AssetLibrary` frame-cache leak, save integrity, the debug overlay, dead code and legacy `src/` modules), then runtime validation on a real J2ME target — the one thing no suite covers. The Phase 8 asset-redistribution review remains the release gate.