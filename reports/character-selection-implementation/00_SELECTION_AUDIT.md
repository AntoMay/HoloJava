# A3 — Character Selection Audit: No Change Required

## 1. Baseline

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Git HEAD | `666e338` | `666e338` | ✅ |
| A1 + A2 working-tree changes | present, unmodified | present, unmodified | ✅ PRESERVED |
| Reference JAR SHA-256 | `20b31de6…3a6dfb` | identical | ✅ |

Diff at start of A3: `AssetLibrary.java` +17/−1, `ContentCatalog.java` +14/−1, `WikiCatalogData.java` +11/−3 (37 insertions, 5 deletions) — identical to end of A2.

---

## 2. Verdict

**NO DEFECT FOUND. No code changed in A3.**

Character selection is already constrained to exactly the five verified characters, because every bound in the selection flow derives from `ContentCatalog.CHARACTERS.length`, which A1 set to 5.

Per the instruction *"Jika audit menunjukkan pilihan karakter sudah aman dan hanya berisi lima karakter, jangan mengubah kode tambahan. Laporkan bukti dan berhenti"* — this report stops at evidence.

---

## 3. Selection Flow Audit

### 3.1 List source

| Question | Answer | Evidence |
|----------|--------|----------|
| Where does the selectable list come from? | `ContentCatalog.CHARACTERS` | `FullGameCanvas.java:187,194,200,315` |
| Does any code read the 47-row CSV at runtime? | **No** | grep `characters.csv` across `src/` returns **only two comments** (`ContentCatalog.java:182`, `WikiCatalogData.java:167`). No parser, no resource read. |
| Is `buildCharacters()` a pass-through? | Yes | A1: returns `BASE_CHARACTERS` verbatim |
| Array size in built JAR | **5** | `iconst_5; anewarray CharacterDef` |

The 47-row CSV is documentation-only and unreachable from the runtime selection path.

### 3.2 Navigation bounds

| Line | Code | Behavior with `CHARACTERS.length == 5` |
|------|------|------------------------------------------|
| `:20` | `CHARACTER_PAGE_SIZE = 8` | 5 < 8 → single page |
| `:187` | `int count = ContentCatalog.CHARACTERS.length + 1;` | `count = 6` (5 chars + Back) |
| `:194` | `int lastPage = (CHARACTERS.length - 1) / CHARACTER_PAGE_SIZE;` | `(5-1)/8 = 0` → RIGHT paging is a no-op ✅ |
| `:198` | `wrap(menuIndex ± 1, count)` | `menuIndex ∈ [0,5]` (see `wrap()` at `:444-448`) |
| `:200` | `if (menuIndex == CHARACTERS.length)` → Back | index 5 returns to MENU ✅ |
| `:201` | `characterIndex = menuIndex;` | only reached for `menuIndex ∈ [0,4]` ✅ |

### 3.3 Entry points that seed `characterIndex`

| Line | Code | Bound check |
|------|------|-------------|
| `:58-59` | `characterIndex = save.selectedCharacter();` then `if (< 0 \|\| >= CHARACTERS.length) characterIndex = 0;` | ✅ clamps a corrupt/legacy value into `[0,5)` |
| `:171` | `case 2: screen = CHARACTERS; menuIndex = characterIndex;` | already clamped |
| `:183` | `... menuIndex = characterIndex;` | already clamped |
| `:211` | `if (left) { screen = CHARACTERS; menuIndex = characterIndex; }` | already clamped |

`save.selectedCharacter()` returns `data[47] & 0x3f` → `0..63`; line 59 reduces any value ≥ 5 to 0. **A legacy save holding index 12 cannot reach the catalog.**

### 3.4 Rendering bounds

| Line | Code | Safety |
|------|------|--------|
| `:315` | `int count = CHARACTERS.length;` | 5 |
| `:316` | `if (menuIndex >= count) characterPage = count / 8;` | `5/8 = 0` ✅ |
| `:319` | `last = Math.min(count, first + 8)` | 5 ✅ |
| `:322` | loop `i < last` → `CHARACTERS[i]` | `i ∈ [0,4]` ✅ |
| `:330` | `if (menuIndex == count)` draws "> Back" | ✅ |
| `:334` | `selected = Math.min(menuIndex, count - 1)` | clamps 5→4 ✅ |
| `:335` | `CHARACTERS[selected]` | safe ✅ |
| `:337` | page count `((count-1)/8)+1` = 1 | "Page 1/1" ✅ |

### 3.5 Downstream consumption

| Consumer | Bound |
|----------|-------|
| `RunWorld.java:145` | `CHARACTERS[characterIndex]` — always `0..4` by §3.2/§3.3 |
| `AssetLibrary.java:18,141,155` | indexed by `0..4` |
| `SaveStore.java:24,30` | reject `characterIndex >= CHARACTERS.length` |

---

## 4. Additional Finding — Corrects an Earlier Audit

While tracing `characterIndex` consumers, I found that **skills and Special Attacks are implemented**, contradicting my earlier report in `reports/gameplay-catalog-audit/00_AUDIT_SUMMARY.md` (finding F3) and `01_CHARACTER_AND_WEAPON_CATALOG.md` §4, which stated they were inert labels.

`RunWorld.java:387-421` dispatches both by **hardcoded `characterIndex` branch**:

| Index | Character | Skill (`activateSkill`) | Special (`activateSpecial`) |
|-------|-----------|------------------------|------------------------------|
| 0 | `gura` | Shark Bite — radial damage r70/d6 | Shark Call — radial damage r110/d18 |
| 1 | `ame` | Hunter's Instinct — enemy slow | Slow Time — 6s enemy slow |
| 2 | `calli` | Undying — shield 20 | The Reaper — heal + 10s lifesteal |
| 3 | `kiara` | Second Flame — 8s lifesteal | Phoenix Fire — radial damage r130/d24 |
| 4 (`else`) | `ina` | The Ancient One — invuln + radial damage | Spirit of the Oni — shield 30 + clear shots |

The names and ordering match the A1 catalog exactly. My earlier grep searched for the `.special` *field* and missed the `activateSpecial()` *method*.

**This strengthens the A3 conclusion**: the 5-character index contract is hardcoded in three independent places (`AssetLibrary.groupIndex`, `RunWorld` skill/special dispatch, and the catalog order). A sixth character would silently inherit Ina's behavior via the `else` branch — so keeping the roster at exactly 5 is correct.

**Not fixed here** — skill/special behavior is explicitly out of A3 scope. The correction is recorded for the B-priority review.

---

## 5. Test Results (cumulative A1 + A2 state)

| Check | Result |
|-------|--------|
| `./tools/build_fullgame.sh` | ✅ **PASS** — JAR 510261 bytes |
| `python3 tools/verify_fullgame_build.py` | ✅ **PASS** — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ clean |
| `CHARACTERS` size in built JAR | ✅ `iconst_5; anewarray CharacterDef` |

No source change was made by A3, so the build output is byte-identical to A2 (510261 bytes).

---

## 6. Boundary Compliance

| Boundary | Status |
|----------|--------|
| A4 / Hard / Time / stage unlock / save / skill / item / collab not touched | ✅ no file modified |
| `ContentCatalog.CHARACTERS` or its order not changed | ✅ untouched |
| 42 documentation entries / CSV / assets not removed | ✅ `git status` shows no change under `data/` or `assets/` |
| No game / MIDlet / emulator | ✅ |
| No download / install | ✅ |
| No commit / reset / stash / history rewrite | ✅ |
| A1 / A2 changes not discarded or overwritten | ✅ diff identical at start and end |
| Reference JAR unchanged | ✅ SHA-256 identical |

---

## 7. Unverified Limitations

| # | Limitation |
|---|-----------|
| U1 | **No runtime confirmation.** Bounds are proven by code reading and bytecode inspection only; no menu was rendered (game execution prohibited). |
| U2 | If `CHARACTERS` ever grows past 5, `RunWorld`'s `else` branches would mis-assign Ina's skill/special. No guard exists — a future catalog expansion must extend both dispatch chains. |
| U3 | `AssetLibrary.characterPortrait` maps only these five ids; a 6th character would fall back to an id-named portrait that does not exist (A2 report L2). |
| U4 | Hard/Time stages remain reachable via the stage screen — that is A3-of-the-roadmap's sibling item (called A3 in the gameplay audit, deferred here per instructions). |

---

## 8. Conclusion

Character selection is **already limited to the five verified characters**. A1 made `ContentCatalog.CHARACTERS` the single source of truth, and every bound in the selection, navigation, rendering, and consumption paths derives from its length. No 47-row documentation path is reachable at runtime.

**No code change was made. A3 requires no implementation work.**

**Awaiting approval before A4.**
