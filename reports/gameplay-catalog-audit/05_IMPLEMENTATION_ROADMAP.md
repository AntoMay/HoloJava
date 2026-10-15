# Implementation Roadmap (Phased)

Recommendations only. **No code was changed.** Each item lists files, dependencies, regression risk, and required tests.

---

## Priority A — Unblock the runtime (blocking everything else)

### A1. Populate the character catalog
| | |
|---|---|
| **Files** | `src/java-me-fullgame/me/holojava/fullgame/WikiCatalogData.java` (or `ContentCatalog.java`) |
| **Change** | Replace `buildCharacters()` stub (`:165-167`) with a 5-entry `CharacterDef[]` matching the reference JAR roster |
| **Data source** | JAR bytecode + `assets/fullgame/characters/*/` — no CSV dependency, no build-script change |
| **Depends on** | nothing |
| **Risk** | LOW — additive data only; no logic touched |
| **Tests** | `ContentCatalog.validate()` returns `null`; game boots; all 5 sprites animate; each character's weapon fires |

Data verified in `01_CHARACTER_AND_WEAPON_CATALOG.md` §2.

### A2. Fix portrait path mismatch
| | |
|---|---|
| **Files** | `AssetLibrary.java:151-158`, or rename 5 PNGs |
| **Change** | Resolve `/assets/fullgame/portraits/<catalog_id>.png` → the actual file (`gawr_gura.png`, `amelia_watson.png`, …). Either add an id map or copy the 5 files under catalog ids. |
| **Depends on** | A1 |
| **Risk** | LOW — asset resolution only |
| **Tests** | All 5 portraits render in the character menu |

### A3. Hide Hard/Time modes from the UI
| | |
|---|---|
| **Files** | `FullGameCanvas.java:307` (mode list), `:208-218` + `:251-255` (stage list) |
| **Change** | Restrict mode select to Stage Mode; filter stage list to `!hard && !timeStage`. **Do not delete** the definitions. |
| **Depends on** | A1 (list rendering depends on catalog length) |
| **Risk** | MEDIUM — navigation indexes shift; `modeIndex` is shared state |
| **Tests** | Stage select lists exactly 5; hard/time unreachable; Back still works |

### A4. Fix `stage5` unlock chain
| | |
|---|---|
| **Files** | `ContentCatalog.java:257` |
| **Change** | `unlockRequirement` `99` → `3` |
| **Depends on** | A1 (save bounds need a non-empty catalog) |
| **Risk** | LOW — one value |
| **Tests** | Fresh save: only stage1 open. Clear stage1 → stage2 opens. Chain through stage5. |
| **Note** | `SaveStore.setCompleted` currently rejects `stageOrder > 7`; with 5 stages this is fine, but the clamp should be revisited when scope grows |

---

## Priority B — Character skills

### B1. Decide the skill data model
| | |
|---|---|
| **Files** | `ContentCatalog.java:48-63` (`CharacterDef`), `RunWorld.java:20,270,282,1180,1263` |
| **Change** | Wiki documents **3 skills per character, 3 upgrade levels each**; `CharacterDef` has one `skill` String. Choose: 3 named skills with effects, or keep 1 placeholder and document the gap. |
| **Depends on** | A1 |
| **Risk** | HIGH — touches offer generation, pickup, and stat recomputation |
| **Tests** | Skill offer appears once per run; effect measurably changes stats; upgrades cap correctly |

### B2. Wire the Special Attack
| | |
|---|---|
| **Files** | `RunWorld.java` (new trigger), `ContentCatalog.java` (`special` field) |
| **Change** | `character.special` currently has **zero** references in gameplay. Needs a charge/activation mechanic. |
| **Depends on** | B1 |
| **Risk** | HIGH — new mechanic |
| **Tests** | Special cannot fire before unlock; fires on input; clears on hit per wiki |

---

## Priority C — Supporting items

### C1. Implement or remove `uber_sheep`
| | |
|---|---|
| **Files** | `RunWorld.java` (add `EFFECT_FOOD_DROP` consumer), `ContentCatalog.java:210` |
| **Change** | `EFFECT_FOOD_DROP` is declared (`:45`) but never applied. Either implement food-drop behavior or remove the item. |
| **Depends on** | A1 |
| **Risk** | LOW — single item |
| **Tests** | Food pickup spawns at the documented rate |

### C2. Guard against `collabs.csv` reaching the classpath
| | |
|---|---|
| **Files** | `ContentCatalog.java:267-291` or `RunWorld.java:1209-1213` |
| **Change** | If `data/` is ever packaged, 26 recipes resolve to `null` weapons → NPE. Add a `weapon(cd.id) == null` guard, or keep `data/` unpackaged and document why. |
| **Depends on** | none |
| **Risk** | LOW — defensive null check |
| **Tests** | Simulate a `CollabDef` with an unknown id; offer generation must not throw |

### C3. Remove dead effect constants (optional)
| | |
|---|---|
| **Files** | `ContentCatalog.java:43,45` |
| **Change** | `EFFECT_SHIELD` unused; `EFFECT_REGEN` handled but unreachable. Document or remove. |
| **Depends on** | none |
| **Risk** | LOW |
| **Tests** | `validate()` and offer generation unaffected |

---

## Priority D — Collabs

### D1. Confirm all five recipes are reachable in-run
| | |
|---|---|
| **Files** | none (verification only) |
| **Change** | Definitions are consistent and reachable **provided** both materials reach level 7 within a run. Balance was not audited. |
| **Depends on** | A1 |
| **Risk** | LOW |
| **Tests** | Force two weapons to level 7; confirm the collab offer appears and materials are consumed |

No D-priority code change is currently required for the 5 ordinary collabs.

---

## Priority E — Requires new mechanics or assets

| # | Item | Why E | Missing |
|---|------|-------|---------|
| E1 | Super Collab (5 recipes) | needs category-3 weapons, an item-pairing rule, and 4 missing prerequisite weapons | `RunWorld.java` offer branch; `BASE_WEAPONS` |
| E2 | 42 remaining characters | needs animation assets | only portraits exist for 42/47 |
| E3 | Wiki-accurate skill effects (5 chars × 3 skills) | needs per-skill design | skill effect data |
| E4 | Outfits / Fandom system | separate wiki systems | no data, no code |
| E5 | Item shop, crates, anvils, stamps | separate wiki systems | no data, no code |

---

## Sequencing

```
A1 (catalog) ──┬─→ A2 (portraits)
               ├─→ A3 (hide modes)
               ├─→ A4 (stage5 unlock)
               ├─→ C1 (uber_sheep)
               └─→ D1 (verify collabs)

A1 ─→ B1 (skill model) ─→ B2 (special attack)
A1 ─→ E1/E2/E3 (new scope)
C2, C3 — independent, any time
```

**Critical path: A1.** Nothing else in this roadmap is verifiable until the character catalog is non-empty.

---

## Regression Risks Summary

| Change | Risk | Mitigation |
|--------|------|------------|
| A1 catalog | LOW | data-only; run `validate()` |
| A2 portraits | LOW | visual only |
| A3 mode hiding | MEDIUM | navigation index shifts; test all screens |
| A4 stage5 | LOW | single value; test unlock chain |
| B1 skills | HIGH | defer until catalog stable |
| C2 collab guard | LOW | null check |

---

## Verification Status

No build, verifier, emulator, or runtime was executed. All recommendations derive from static reading of source, assets, and JAR bytecode.

**Awaiting explicit approval before any implementation.**
