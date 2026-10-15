# HoloJava — Gameplay Catalog Audit: Summary

## 1. Baseline Verification

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Git HEAD | `666e338` | `666e338` | ✅ MATCH |
| `git status --short` | clean working tree | only untracked `reports/*` dirs; **0 modified tracked files** | ✅ PASS |
| Reference JAR SHA-256 | `20b31de6b6e5bced56959ee3d11cd0d698508518ae061dffe0780563b53a6dfb` | identical | ✅ MATCH |

No reset, checkout, or working-tree mutation was performed.

---

## 2. Evidence Basis

| Source | Method | Used for |
|--------|--------|----------|
| `ContentCatalog.java` (432 L) | direct read | catalogs, factory, `validate()` |
| `WikiCatalogData.java` (176 L) | direct read | CSV parser, `buildCharacters()` stub |
| `RunWorld.java` (1646 L) | targeted grep | effects actually executed |
| `FullGameCanvas.java` (459 L) | targeted grep | stage/mode gating |
| `AssetLibrary.java` (246 L) | targeted read | asset path resolution |
| Reference JAR | `unzip -l`, `javap -c -p` | shipped catalog, sprite set, legacy masks |
| `data/wiki/*.csv` | targeted read | documentation-grade reference data |
| HoloCure Wiki | `webfetch` Character page | roster count, skill/special structure |

---

## 3. Headline Findings

| # | Finding | Class | Evidence |
|---|---------|-------|----------|
| **F1** | `CHARACTERS` is **empty at runtime** — `buildCharacters()` returns `new CharacterDef[0]` | FACT | `WikiCatalogData.java:165-167` |
| **F2** | Reference JAR shipped a **5-entry** `CHARACTERS` array with skill names | FACT | JAR `anewarray` size 5 |
| **F3** | **Skill and Special Attack have zero gameplay effect** — offer selection sets only a boolean | FACT | `RunWorld.java:1263-1264` |
| **F4** | Item effects: **9 of 11 implemented**, 2 dead | FACT | `RunWorld.java:1123-1139`, `:969` |
| **F5** | `EffectDef.sprite` for items is stored but **never used to draw** | FACT | `RunWorld.java:741,1454` only use `def.sprite` for shots |
| **F6** | Collab recipes: **5 reachable**, but a latent NPE exists if the CSV is ever packaged | FACT | `RunWorld.java:1209-1213` + `ContentCatalog.java:410` |
| **F7** | Super Collab: **no implementation** — no recipe reaches a super-collab weapon | FACT | `BASE_WEAPONS` has no category-3 weapon |
| **F8** | Portrait path mismatch: portraits named by wiki id, code resolves by catalog id | FACT | `AssetLibrary.java:155` vs `assets/fullgame/portraits/*.png` |
| **F9** | Stage 1–5 are selectable today; Hard/Time also reachable via mode select | FACT | `FullGameCanvas.java:251-262` |

---

## 4. Catalog Readiness Snapshot

| Domain | Status | Blocker |
|--------|--------|---------|
| Characters (5) | `UNRESOLVED` | catalog empty → runtime crash at `AssetLibrary.java:18` |
| Basic attacks (5) | `IMPLEMENTED` | none — patterns fully dispatched |
| Skills | `DATA_ONLY` | label only, no effect |
| Specials | `DATA_ONLY` | field exists, zero references in gameplay |
| Items | `PARTIAL` | 2 effects dead; sprites unused |
| Collabs | `IMPLEMENTED` | 5 recipes, reachable; latent CSV NPE |
| Super Collabs | `UNKNOWN` | absent from catalog and gameplay |
| Stage 1–5 | `IMPLEMENTED` | scope creep: Hard/Time also exposed |

---

## 5. Key Structural Risk

`ContentCatalog.CHARACTERS.length == 0` invalidates **every** downstream consumer:

| Consumer | Failure mode |
|----------|--------------|
| `AssetLibrary.java:18` | `new CharacterAssets[0]` |
| `RunWorld.java:145` | `CHARACTERS[characterIndex]` → `ArrayIndexOutOfBoundsException` |
| `RunWorld.java:208` | `character.hp` → NPE |
| `SaveStore.java:24,30` | every `isCompleted`/`setCompleted` returns false/no-op |
| `FullGameCanvas.java:187` | character menu shows only "Back" |

**Every other finding in this audit is downstream of F1.** Progress tracking cannot function at any save format until the catalog is non-empty.

---

## 6. Reports

| File | Contents |
|------|----------|
| `00_AUDIT_SUMMARY.md` | this file |
| `01_CHARACTER_AND_WEAPON_CATALOG.md` | 5 characters, sprites, weapons, ID validation |
| `02_ITEM_CATALOG.md` | 11 items, defined vs applied effects |
| `03_COLLAB_RECIPES.md` | 5 collab recipes, super-collab status |
| `04_STAGE_SCOPE.md` | Stage 1–5 scope, extra modes, dependencies |
| `05_IMPLEMENTATION_ROADMAP.md` | phased recommendations A–E |

---

## 7. Confidence Legend

- **FACT** — directly read from source, asset path, or bytecode
- **INFERENCE** — derived by combining two verified facts
- **BELUM TERVERIFIKASI** — could not be confirmed within this audit's constraints

**Status: COMPLETE. Awaiting review before any implementation.**
