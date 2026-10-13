# HoloJava Project Status

**Baseline Commit:** `b1239e546068de51c3265f78a492dc7343d92aea`
**Initial Commit:** `Initial HoloJava project baseline`
**Git Status:** Clean working tree

## Project Overview
- **Target Platform:** Java ME CLDC 1.1 / MIDP 2.0
- **Display Resolution:** 320×240 landscape
- **Build Method:** Offline using local dependencies
- **Emulator:** Not executed (desktop Linux session loss risk)

## Baseline Status
- Git repository initialized at project root
- `.gitignore` created without broad `*.jar` patterns
- 320 files tracked in initial commit
- Source code, assets, dependencies, and documentation all baseline-committed

## Key Source Files Audit
- `FullGameMIDlet.java` - MIDlet lifecycle, save store integration, content validation
- `FullGameCanvas.java` - Menu flow (6 screens: MAIN, MODES, CHARACTERS, STAGES, SETTINGS, PREP), game loop, input handling, rendering, state transitions
- `RunWorld.java` - Deterministic bounded simulation, entity pools (ENEMY_POOL=48, SHOT_POOL=128, MELEE_POOL=24, PICKUP_POOL=96), player state (hp, level, experience, coins, kills), game states (RUNNING, PAUSED, LEVEL_UP, GAME_OVER, VICTORY)
- `Viewport.java` - Viewport management for 320×240 rendering
- `ContentCatalog.java` - Character definitions (47 documented), weapon definitions (94 documented including BASE_WEAPONS and WikiCatalogData-derived), item definitions (37), enemy definitions, boss definitions, mode definitions (3: STAGE, ENDLESS, TIME), stage definitions (16), collision and behavior constants

## Dependency Local
- `deps/java-me/cldcapi11-2.0.4.jar` - CLDC 1.1 API
- `deps/java-me/midpapi20-2.0.4.jar` - MIDP 2.0 API

## .gitignore Rules
- `build/` - Build output directory
- `dist/` - Distribution output directory
- `__pycache__/` - Python bytecode cache
- `recovery/` - Recovery data directory
- `rebuild-*/` - Rebuild session directories
- `.DS_Store` - macOS directory metadata
- `*.swp` / `*.swo` - Vim swap files
- No broad `*.jar` pattern (per task rule 3)

## Audit Findings
See `docs/roadmap/GAMEPLAY_ROADMAP.md` for detailed gameplay phase breakdown.

**Confirmation:** Source gameplay code not modified. Emulator not executed.
