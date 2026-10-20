# HoloJava

A HoloCure-style survival roguelite for Java ME (CLDC 1.1 / MIDP 2.0). Pick an
idol, survive the stage's waves, beat the 10:00 and 20:00 bosses, and spend the
coins you collect in a shop that persists between runs. The game is entirely
data-driven: twelve Java files (~7,800 lines) behind one catalog table
(`ContentCatalog`), which carries the source URL of every documented figure
next to the figure itself.

Reference content — stages, idols, weapons, items, bosses — comes from
HoloCure and the HoloCure wiki; one stage and its cast are original to this
project and labelled as such. This is non-commercial fan work; see
[Credits](#credits).

## Requirements

- **JDK** — any `javac` that supports `--release 8`. Built with OpenJDK 21,
  producing class file version 52.
- **Python 3** — build verification and the CLDC bytecode gate (standard
  library only).
- **sh** — build and test entry points are POSIX shell scripts.
- **FreeJ2ME-Plus** — to play the desktop build. The mobile jar sideloads on a
  phone or runs in any other J2ME emulator.

## Build

```sh
sh tools/build_all.sh
```

Cleans and rebuilds `dist/` from scratch:

- `dist/Public/mobile/HoloJavaFullGame.jar` — preverified jar for a phone
- `dist/Public/desktop/HoloJavaFullGame.jar` — for FreeJ2ME-Plus

The same pair also lands under `dist/DevBuild/`, compiled with a large starting
coin capital so the shop can be driven without grinding; public builds start at
zero coins. See `ShopStartingCapital.java` for the difference.

## Test

```sh
sh tools/run_fullgame_tests.sh                 # every suite (41 suites, ~4,300 checks)
sh tools/run_fullgame_tests.sh BossStatsWikiTest   # a single suite
```

The runner compiles the production sources through the CLDC gate, compiles the
suites under `tools/tests/`, and runs every `*Test` main. It also compiles a
deliberately non-CLDC fixture on every run, so the gate is proven to still
reject violations instead of silently rotting.

## Play

The build prints the exact command; it is essentially:

```sh
java -Dfile.encoding=ISO_8859_1 -jar /path/to/freej2me_plus.jar \
    "file:///…/dist/Public/desktop/HoloJavaFullGame.jar" 0 320 240 3
```

`-Dfile.encoding` must come **before** `-jar` — FreeJ2ME-Plus renders nothing
on an encoding mismatch. Arguments after the jar path: `0` = windowed mode,
`320 240` = LCD size, `3` = scale.

## Layout

| Path | What lives there |
| --- | --- |
| `src/java-me-fullgame/` | The game: the current full-game runtime (12 files). |
| `src/java-me-first-slice/`, `src/java-me-phase3/`, `src/java-me-diagnostic/` | Earlier milestones, kept for reference; not part of the build. |
| `tools/` | Build scripts, the CLDC bytecode gate, and every test suite (`tools/tests/`). |
| `assets/` | Packaged game assets: animation sets, stages, UI. |
| `assets-source/` | Source imagery behind the original stage's generated art. |
| `data/wiki/` | CSVs extracted from the HoloCure wiki (characters, weapons, items, stages, bosses…); every row keeps its source URL and coverage note. |
| `deps/java-me/` | Pinned CLDC 1.1 and MIDP 2.0 API stub jars — the definition of what the game may call. |
| `docs/` | Contracts, decisions and status. `docs/PROJECT_STATUS.md` is the source of truth. |
| `reports/` | Audit snapshots and manifests produced by tooling. |
| `build/`, `dist/`, `rms/`, `freej2me_system/` | Generated: classes, artifacts, emulator and save state. Git-ignored. |

## The CLDC gate

The desktop JDK happily compiles code a phone cannot run — string
concatenation alone becomes `java.lang.StringBuilder`, which CLDC does not
have. Legality is therefore checked on the bytecode: after every production
compile, `tools/verify_cldc_classes.sh` rewrites `StringBuilder` to
`StringBuffer` and checks every `java.*`/`javax.*` reference in the class
files against the pinned stub jars (`tools/check_cldc_compliance.py`). A
missing symbol fails the build, and the mobile jar then runs through ProGuard
as a second gate over exactly what ships.

## Where the numbers come from

The catalog separates documented figures from invented ones instead of
blending them:

- **Wiki-sourced rows** carry `documentedBaseStats = true`: stage and boss
  timing from the wiki's Stage pages, boss HP/ATK/EXP from each stage page's
  Enemy Composition table, idol/weapon/item data from `data/wiki/*.csv`.
- **Approximated rows** — the regular-enemy stats — are flagged `false`; the
  comment above `ContentCatalog.ENEMIES` explains why no source exists for
  them and that they must not be tuned as if one did.
- **Original content** — stage6 "Arena Hutan Sawit", its enemies and its
  bosses WoWo/FufuFafa — is this project's own design, labelled in the catalog
  and in `docs/PROJECT_STATUS.md`.

## Documentation

- `docs/PROJECT_STATUS.md` — current phase, blockers, immediate next action
  (source of truth)
- `Roadmap.md` — planned phases
- `docs/TEST_PLAN.md`, `docs/TECHNICAL_DECISIONS.md`,
  `docs/GAME_MECHANICS.md`, `docs/ASSET_USAGE.md` — contracts and decisions

## Credits

- **HoloCure**, the free hololive fan game by Kay Yu — the reference for this
  project's content; extracted visual assets are used under the policy
  described in `docs/ASSET_USAGE.md`.
- **HoloCure wiki** (https://holocure.wiki.gg/) — the documented figures the
  catalog cites.

Fan work, not affiliated with hololive or Cover Corp.
