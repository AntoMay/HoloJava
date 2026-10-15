# A4a — Disable Hard Mode and Time Mode in the UI

## 1. Baseline

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Git HEAD | `666e338` | `666e338` | ✅ |
| A1 + A2 changes | present | present, unmodified (`AssetLibrary.java` +17/−1, `ContentCatalog.java` +14/−1, `WikiCatalogData.java` +11/−3) | ✅ PRESERVED |
| Reference JAR SHA-256 | `20b31de6…3a6dfb` | identical | ✅ |

---

## 2. Audit — Input Paths That Could Reach Hard or Time

### 2.1 `modeIndex` write sites

Grep across `FullGameCanvas.java` + `RunWorld.java` found exactly **two** writes to `modeIndex`:

| Line | Code |
|------|------|
| `:60` | `modeIndex = 0;` (constructor) |
| `:183` | `modeIndex = menuIndex;` (inside `updateModes`) |

`RunWorld.modeIndex` is `final` (`:39`) and assigned only from the constructor parameter (`:143`). **No save-file path writes `modeIndex`.**

### 2.2 Reachable paths before the fix

| # | Path | Reached | Evidence |
|---|------|---------|----------|
| P1 | Mode screen list | **Endless (1) and Time (2)** | `drawModes:307` listed 4 items; `updateModes:180` `wrap(…, 4)`; `:182-183` committed `modeIndex = menuIndex` |
| P2 | Stage screen | **Hard variants (catalog idx 5–8)** | `updateStages:209` `count = STAGES.length + 1` = 11; `drawStages:348` looped all 10; `stageAllowed:253-254` returns `!s.timeStage` under Stage Mode, which **admits `hard == true`** |

### 2.3 Paths checked and found safe

| Candidate | Finding |
|-----------|---------|
| Keyboard shortcut to a mode/stage | None — input is strictly screen-dispatched at `:155-162`; no global shortcut |
| Direct `screen = PREP` | Only `:217`, reachable solely from `updateStages` after the stage filter |
| `screen = STAGES` from main menu | `:172` `menuIndex = 0` → normal stage 1, consistent with the filtered list |
| `screen = STAGES` from Prep | `:251-259` — **fixed**, see §3.4 |
| Save data forcing a mode | None — `modeIndex` is never persisted |

---

## 3. Changes Applied

Single file: `src/java-me-fullgame/me/holojava/fullgame/FullGameCanvas.java`

### 3.1 Mode screen — `updateModes` (`:179-186`)

```java
if (up || down) menuIndex = wrap(menuIndex + (up ? -1 : 1), 2);   // was 4
if (menuIndex == 1) { screen = MENU; menuIndex = 1; }             // was 3
else { modeIndex = 0; ... }                                       // was menuIndex
```

`modeIndex` is now **pinned to 0** — Stage Mode.

### 3.2 Mode screen — `drawModes` (`:331-338`)

List reduced to `{ "Stage Mode", "Back" }`; footer text updated.

### 3.3 Stage screen — new `selectableStages()` helper (`:221-235`)

```java
private static int[] selectableStages() {
    ...
    if (!s.hard && !s.timeStage) buffer[count++] = i;
    ...
}
```

Returns catalog indices `[0,1,2,3,4]`. **`stageIndex` still stores the real catalog index**, so `RunWorld:148` (`STAGES[stageIndex]`) and `AssetLibrary:98` (`stage(index)`) are unaffected.

`updateStages` (`:210-222`) and `drawStages` (`:373-388`) now iterate menu **slots** mapped through this array.

### 3.4 Prep → Stage return — `updatePrep` (`:253-262`)

Previously `menuIndex = stageIndex` (raw index). With a filtered list that mapping is only coincidentally correct, so it now resolves the stage index back to its slot:

```java
for (int slot = 0; slot < stages.length; slot++) if (stages[slot] == stageIndex) { menuIndex = slot; break; }
```

This keeps the filter genuinely reversible.

### 3.5 Design rationale

Both gates are **filters over existing data**, not deletions:

| Preserved | Evidence |
|-----------|----------|
| `ContentCatalog.MODES` — all 3 entries | built JAR: `iconst_3; anewarray ModeDef` ✅ |
| `COMPLETE_*` constants | untouched |
| `ContentCatalog.STAGES` — all 10 entries | built JAR: `bipush 10; anewarray StageDef` ✅ |
| `stageAllowed()` / `stageUnlocked()` | untouched |
| `RunWorld` mode/gameplay logic | 4 `MODES`/`allowsCollabs`/`standardLoadout` references still present ✅ |
| Re-enable procedure | widen `selectableStages()` + restore the mode list |

---

## 4. Test Results

| Check | Result |
|-------|--------|
| `./tools/build_fullgame.sh` | ✅ **PASS** — JAR 510475 bytes |
| `python3 tools/verify_fullgame_build.py` | ✅ **PASS** — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ clean |
| A1/A2 diff unchanged | ✅ 37 insertions / 5 deletions, same as after A2 |

Compiled-string verification from the built JAR:

```
"Stage Mode"                          ← only mode entry
"Back"                                ← only exit
"Endless and Time modes are not available yet."
"Hard and Time stages are not available yet."
```

No `"Endless Mode"` or `"Time Mode"` selectable entry remains. The string `"Hardcore sets Endless player HP to 1."` persists in `drawSettings` — that is the hardcore-setting description, not a selection path, and was intentionally left alone.

---

## 5. Boundary Compliance

| Boundary | Status |
|----------|--------|
| A4b / stage progression / save not started | ✅ untouched |
| Unlock stage, progress persistence, victory condition, stage order unchanged | ✅ `stageUnlocked`, `stageAllowed`, `:149` `setCompleted`, and the `STAGES` array are all untouched |
| Character catalog, portrait, skill/special, item, collab unchanged | ✅ those files not modified by A4a |
| No enum/constant/data/class/asset/gameplay logic removed | ✅ verified in §3.5 |
| No game / MIDlet / emulator | ✅ |
| No download / install | ✅ |
| No commit / reset / stash / history rewrite | ✅ |
| A1–A2 changes preserved | ✅ |
| Reference JAR unchanged | ✅ |

---

## 6. Unverified Limitations

| # | Limitation |
|---|-----------|
| U1 | **No runtime confirmation.** Blocking is proven by code reading and compiled-string inspection only; no menu was rendered (game execution prohibited). |
| U2 | `menuIndex` entering the mode screen is seeded from `modeIndex` (`:170`), which is now always 0 — the screen always opens on "Stage Mode". Correct, but not exercised at runtime. |
| U3 | The Settings screen still describes hardcore as *"sets Endless player HP to 1"*. Endless is unreachable, so this text is now aspirational. Left unchanged as out of scope. |
| U4 | `startRun()` (`:245`) still re-checks `stageUnlocked`/`stageAllowed` as defense in depth. Unchanged; it does not widen access. |
| U5 | `stage5.unlockRequirement == 99` still makes Fantasy Island permanently open. That is **A4b**, deliberately not touched. |

---

## 7. Conclusion

Hard and Time are no longer selectable through any UI path. All underlying data and gameplay logic are preserved and reachable again by widening two filters.

**A4a complete. Awaiting approval before A4b.**
