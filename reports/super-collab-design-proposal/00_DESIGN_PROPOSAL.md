# Super Collab — Design Proposal (AUDIT & DESIGN ONLY)

| Item | Value |
|------|-------|
| Checkpoint | `c7764a8` (`Remove unsupported Uber Sheep item`) |
| Working tree | clean (tracked) |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` ✅ |
| Reference version | HoloCure **0.7.1731644049** (2024-11-15), <https://holocure.wiki.gg/wiki/Super_Collab> |

**No code was changed. No commit. No build was run — every fact below is a static source read, so a build would add nothing. No game or emulator was run; nothing was downloaded or installed.**

---

## 1. Verified Facts

### 1.1 The five active Collab recipes and their source

`ContentCatalog.java:277-303` — a static initialiser tries `/data/wiki/collabs.csv`, then falls back to a hardcoded array. The CSV is **not packaged**: `build_fullgame.sh:11` copies only `assets/fullgame`, `:32` zips only `META-INF assets me`; `unzip -l` on the built JAR shows **0** `data/` entries. `getResourceAsStream` therefore always returns `null`.

**Loaded at runtime: 5 recipes** (`ContentCatalog.java:295-303`).

| # | Result | `weaponA` | `weaponB` | WeaponDef | maxLevel |
|---|--------|-----------|-----------|-----------|----------|
| 1 | Absolute Wall | `bounce_ball` | `cutting_board` | `:202` | 7 |
| 2 | BL Fujoshi | `bl_book` | `psycho_axe` | `:203` | 7 |
| 3 | Black Plague | `ens_curse` | `owl_dagger` | `:204` | 7 |
| 4 | Blood Lust | `owl_dagger` | `psycho_axe` | `:205` | 7 |
| 5 | Legendary Sausage | `bl_book` | `sausage` | `:206` | 7 |

All 20 weapons declare `maxLevel = 7`.

### 1.2 `CollabDef` structure and the meaning of `type`

`ContentCatalog.java` `CollabDef`: `id`, `name`, `weaponA`, `weaponB`, `type`, `source` — set by the `collab(...)` factory.

- `weaponA` / `weaponB` are **weapon ids**.
- `type` holds the CSV string `"collab"` or `"super_collab"` (parsed at `WikiCatalogData.java:140`).
- **`type` is never read by any runtime logic.** `RunWorld` uses only `cd.weaponA`, `cd.weaponB`, `cd.id`. `WikiCatalogData.isValidCollab` reads `def.type` but has **zero callers**.

The field is therefore **inert metadata**. This proposal does **not** adopt it as a runtime discriminator (see §6, Decision D-1).

### 1.3 How the runtime checks levels, consumes materials, and creates the result

| Concern | Code | Behaviour |
|---------|------|-----------|
| Offer trigger | `RunWorld.java:1208-1218` | `weaponCount < 6 && mode.allowsCollabs`, then per recipe: `weaponLevelById(cd.weaponA) == wd.maxLevel && weaponLevelById(cd.weaponB) == wd.maxLevel` |
| Absent material | `weaponLevelById` helper | returns **0** when not held, so it can never satisfy `== 7` |
| Consumption | `RunWorld.java:1267-1270` | `removeWeaponById(def.prerequisiteA); removeWeaponById(def.prerequisiteB); addWeapon(o.value, 1);` |
| Result level | same | result added at **level 1** |
| Mode gate | `ContentCatalog.java:247-249` | Stage ✅, Endless ✅, Time ❌ |

### 1.4 Reusable state

| State | Location | Notes |
|-------|----------|-------|
| Weapon slots | `RunWorld.java:47-49` | `weaponSlots[6]`, `weaponLevels[6]`, `weaponReadyAt[6]`; cap `weaponCount < 6` |
| Item slots | `RunWorld.java:50-51` | `itemSlots[6]`, `itemLevels[6]`; cap `itemCount < 6` |
| Item lookup | `itemSlotById(String)` `:1333` | returns slot or `-1` — **reusable** |
| Weapon lookup | `weaponSlotById` / `weaponLevelById` | **reusable** |
| Player level | `RunWorld.java:71` | `private int playerLevel = 1;` |
| Level curve | `requiredForLevel(int)` `:1310-1317` | `79` for level 1→2, else `(4(n+1))^2.1 − (4n)^2.1` |
| Run reset | `reset(SaveStore)` `:160-…` | resets `playerLevel=1`, `weaponCount=0`, `itemCount=0`, all slot arrays, `skillAcquired=false`, `specialUsed=false` |

**Gap identified**: there is **no `itemLevelById` helper** (unlike `weaponLevelById`). A Super Collab check needs it.

**Item max levels are per-item, not uniform**: Face Mask 1, Knightly Milk 3, Energy Drink 3, Just Bandage 3, Study Glasses 5, Sake 3, Gorilla's Paw 3, Piki Piki Piman 3, Limiter 3, Headphones 5 (`ContentCatalog.java:211-222`).

### 1.5 What is genuinely absent for Super Collab

`grep -rni "golden\|anvil" src/` → **no matches**.

| Element | Present? |
|---------|----------|
| Golden Hammer | ❌ |
| Golden Anvil | ❌ |
| Anvil (any kind) | ❌ |
| Super Item concept | ❌ |
| Category-3 weapon | ❌ (`BASE_WEAPONS` uses 0, 1, 2 only) |
| Runtime super-collab offer branch | ❌ (`RunWorld:1267` has only the `o.collab` branch) |
| Per-run collab counter | ❌ |
| OFFER type for a hammer | ❌ — `OFFER_*` are only `SKILL, WEAPON, ITEM, STAT, FOOD, COINS, REROLL, HOLD` (`RunWorld.java:20-27`) |

---

## 2. Hard Constraint the Design Must Respect

The five CSV super collabs **cannot be used as-is**. Four of their four required collab weapons are undefined in `BASE_WEAPONS`:

| Recipe | Required collab weapon | Defined? | Required item | Defined? |
|--------|------------------------|----------|---------------|-----------|
| Holy Fire | Dragon Fire | ❌ | Gorilla's Paw | ✅ (maxLv 3) |
| Idol Live | Idol Concert | ❌ | Idol Costume | ❌ |
| Jingisukan | Elite Cooking | ❌ | Uber Sheep | ❌ (removed in `c7764a8`) |
| Snow Queen | Snow Flower Sake | ❌ | Sake | ✅ (maxLv 3) |
| True Infinite BL Works | **BL Fujoshi** | ✅ | Hope Soda | ❌ |

Of the five **active** collabs, only `bl_fujoshi` appears in any super recipe — and that recipe is blocked by the missing `hope_soda`.

**Consequence: with the current catalog, zero Super Collabs are constructible.** The feature needs new weapons and items regardless of how the rule logic is written.

---

## 3. Proposed Flow

```
                    ┌─────────────────────────────────────────┐
                    │  Existing Collab path (UNCHANGED)       │
                    │  RunWorld:1208-1218  trigger             │
                    │  RunWorld:1267-1270  consume A+B → make │
                    └─────────────────────────────────────────┘
                                    │
                          collab created; collabCount++
                                    │
        ┌───────────────────────────┴───────────────────────────┐
        │  Super Collab gate (NEW, run-scoped state)            │
        │                                                      │
        │  1. goldenHammer == true ?          ── no → skip      │
        │  2. superCollabUsed == true ?      ── yes → skip      │
        │  3. weaponCount >= 2 (room for result + consumables)? │
        │  4. For each SUPER_RECIPE:                            │
        │       a. result weapon present?                      │
        │       b. companion item present at ITS maxLevel?      │
        │       c. offer OFFER_WEAPON (collab=true, super=true)│
        └──────────────────────────────────────────────────────┘
                                    │ accepted
                                    ▼
              consume collab weapon + consume companion item
                                    │
                    add result weapon at level 1
                                    │
                    superCollabUsed = true; goldenHammer = false
```

Failure paths (all silent, never destructive):

| Condition | Behaviour |
|-----------|-----------|
| No golden hammer | No super offer generated |
| Already used one this run | No super offer generated |
| Companion item not at max level | Recipe not offered |
| Weapon slots full | No offer generated (`weaponCount` guard) |
| Unknown/unreachable recipe id | Guarded lookup — never dereference null |

---

## 4. Recommended Rules vs Decisions Needing Approval

### 4.1 Recommended (safe, matches existing conventions)

| # | Rule | Rationale |
|---|------|-----------|
| R1 | Reuse `weaponLevelById` / `weaponSlotById`; add a parallel `itemLevelById` helper | Mirrors the existing weapon pattern exactly |
| R2 | Companion item must be at **its own** `ItemDef.maxLevel` | Matches reference; item caps vary (1/3/5) so a hardcoded 7 would be wrong |
| R3 | Consume **both** the collab weapon and the companion item, mirroring `RunWorld:1268-1269` | Matches reference and existing consumption semantics |
| R4 | Result added at **level 1** | Matches existing collab behaviour |
| R5 | All new state is **run-scoped** and reset in `reset(SaveStore)` | `reset()` already clears every comparable field |
| R6 | Recipe list must be a **separate hardcoded array** in `ContentCatalog`, not the CSV | CSV is not packaged; loading it would introduce 26 unreachable recipes and a known null-deref path |
| R7 | Guard `weapon(cd.id) == null` before dereference | Prevents the latent NPE identified in `reports/skill-collab-audit` |
| R8 | Nothing is written to RMS | Super Collab is a per-run mechanic; save format untouched |

### 4.2 Requires explicit user approval before implementation

| # | Decision | Reference value | Why it needs a decision |
|---|----------|-----------------|-------------------------|
| **D-1** | How to mark a recipe as "super" | — | **`CollabDef.type` is inert and its contract is unproven.** Options: (a) reuse `type` after fixing its contract, (b) add a **new** `SuperCollabDef` class. Task constraint forbids adopting `type` before its meaning is settled → **recommend (b)** |
| **D-2** | Golden Hammer gate | `playerLevel >= 50` **OR** `>= 3 collabs` | See §5 feasibility note. Is either gate reachable in a HoloJava run? |
| **D-3** | Can Super Item replace the companion item | yes, in reference | HoloJava has **no Super Item concept at all**. Recommend **no** for v1 |
| **D-4** | One Super Collab per run | yes, in reference | Confirm. Currently no per-run counter exists for ordinary collabs either |
| **D-5** | Item consumed on craft | yes, in reference | Confirm — it frees a slot, which is meaningful given the 6-slot cap |
| **D-6** | Golden Hammer as pickable object vs silent auto-grant | reference: ground object | HoloJava has no world-object system for tools (only `Pickup`). A pickup type is possible; a HUD-held item is not |
| **D-7** | Which recipes to author | — | §2 shows **none** are currently constructible. Authoring new weapons/items is a content decision beyond this proposal |

---

## 5. Feasibility Note on the Level-50 Gate (INFERENCE, not proven)

`requiredForLevel(n)` returns `4^2.1 · ((n+1)^2.1 − n^2.1)`, growing with `n`. Summing levels 1→50 gives an EXP total on the order of tens of thousands.

Available EXP per run: enemies `7–20`, bosses `40–180`, plus the type-4 weapon-level drop and `study_glasses` (maxLv 5) bonuses.

**Inference**: reaching level 50 in a single run is likely rare, which would make the `>= 3 collabs` path the practical trigger. **This is an estimate, not a measurement** — no run was simulated. It is flagged because it affects whether D-2 is worth implementing at all.

---

## 6. Files That May Need Changes

| File | Why | Confidence |
|------|-----|------------|
| `ContentCatalog.java` | Add a `SuperCollabDef` structure + hardcoded recipe array; add `superCollab` result weapons (category 3) | Required |
| `RunWorld.java` | Run state (`goldenHammer`, `superCollabUsed`), `itemLevelById` helper, offer trigger + consumption branch, `reset()` additions | Required |
| `WikiCatalogData.java` | Only if `CollabDef.type` is adopted — **not recommended** | Conditional |
| `SaveStore.java` | **None** — Super Collab is run-scoped | Not required |
| `FullGameCanvas.java` | Only if a HUD indicator for the hammer is wanted | Conditional |
| `build_fullgame.sh` | **None** — recipes stay hardcoded | Not required |

---

## 7. Risks

| # | Risk | Severity | Note |
|---|------|----------|------|
| S1 | Zero constructible recipes today | **HIGH** | Blocks the entire feature until content exists (D-7) |
| S2 | Golden Hammer requires a world-object or new offer type | MEDIUM | No existing mechanism fits cleanly (D-6) |
| S3 | `CollabDef.type` misuse would silently mis-key recipes | MEDIUM | Avoided by Decision D-1 |
| S4 | Level-50 gate likely unreachable | MEDIUM | See §5 |
| S5 | Consuming the companion item at max level is a real loss | LOW | Expected behaviour; matches reference |
| S6 | Adding state to `reset()` risk | LOW | `reset()` is a flat list; omission would leak state across runs — **must test** |

---

## 8. Required Tests

| # | Test | Expected |
|---|------|----------|
| T1 | No hammer → no super offer | pass |
| T2 | Hammer + valid collab + max-level item + free slot | offer appears |
| T3 | Same, companion item **below** max level | no offer |
| T4 | Accept super offer | both weapon and item removed, result at level 1 |
| T5 | Accept a second super offer in the same run | impossible (`superCollabUsed`) |
| T6 | New run | hammer and `superCollabUsed` both cleared |
| T7 | Recipe id with no weapon definition | guarded, no NPE |
| T8 | Item cap differs (maxLv 1 vs 3 vs 5) | each uses its own cap |
| T9 | Save round-trip after a super collab | unchanged — RMS untouched |
| T10 | Existing 5 collab recipes | byte-identical behaviour to `c7764a8` |

**Harness note**: `RunWorld` cannot be instantiated outside a MIDlet — the MIDP stubs throw `java.lang.Error`, which `AssetLibrary.image()`'s `catch (Exception)` does not catch (established in the EXP-fix stage). T1–T8 therefore need either a real device or a deliberate extraction of the predicate into a pure static method, which would be a structural change beyond this proposal.

---

## 9. Suggested Implementation Phases

| Phase | Scope | Gate |
|-------|-------|------|
| **0** | **Design decisions only** — answer D-1 … D-7 | User approval |
| **1** | Add `SuperCollabDef` + recipe array + category-3 weapon stubs (inert) | `validate()` still passes |
| **2** | Add `itemLevelById` + run state + `reset()` wiring | No gameplay effect yet |
| **3** | Implement offer trigger + consumption | T1–T8 |
| **4** | Author real recipes and required items/weapons | Content approval |
| **5** | HUD indicator (only if D-6 warrants it) | Visual review |

**Phase 0 must complete first.** Every later phase depends on decisions that are not derivable from the code.

---

## 10. Explicit Non-Goals

- The five active Collab recipes are **not modified**.
- **No Anvil or general weapon-upgrade system** is introduced — noted as a separate feature if parity is wanted.
- **Skill Collab is not assumed to exist** — `grep` over `src/`, `data/`, and `docs/` found nothing; it is a distinct concept and is out of scope here.
- `CollabDef.type` is **not** adopted as a runtime mechanic without a prior contract decision (D-1).

---

## 11. Final Status

| Item | Value |
|------|-------|
| HEAD | `c7764a8` — unchanged |
| Tracked modifications | **none** |
| Commit | none |
| Reference JAR | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` — unchanged |
| Only new file | `reports/super-collab-design-proposal/00_DESIGN_PROPOSAL.md` |
