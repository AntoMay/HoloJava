# A4b-R1 / R-3 — Lock Stage 5 Behind Stage 4

## 1. Baseline

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Git HEAD | `666e338` | `666e338` | ✅ |
| A1 + A2 + A4a changes | present | present, unmodified | ✅ PRESERVED |
| Reference JAR SHA-256 | `20b31de6…3a6dfb` | identical | ✅ |

---

## 2. Pre-change Rule Confirmation

The assumption was verified against source before editing. `ContentCatalog.java:265-269`, `unlockRequirement` is the 14th argument of `stage(id, name, hard, timeStage, order, tile, palette, eA, eB, eC, spawnInterval, maxEnemies, coinClear, unlockRequirement, …)`:

| Stage | `order` | `unlockRequirement` | Meaning under `stageUnlocked` |
|-------|---------|---------------------|--------------------------------|
| `stage1` | 0 | `-1` | always unlocked |
| `stage2` | 1 | `0` | requires order 0 = Stage 1 |
| `stage3` | 2 | `1` | requires order 1 = Stage 2 |
| `stage4` | 3 | `2` | requires order 2 = Stage 3 |
| `stage5` | 4 | **`99`** | **always unlocked** — breaks the chain |

The convention is *"the previous stage's `order`"*. Stage 5 therefore needs **`3`**. Assumption matched; implementation proceeded.

---

## 3. Changes Applied

Two edits, both in `src/java-me-fullgame/me/holojava/fullgame/ContentCatalog.java`.

### R-1 — `:269`

```diff
-        stage("stage5", "Fantasy Island", false, false, 4, "fantasy", 4, …, 15000, 99, "nanora", …),
+        stage("stage5", "Fantasy Island", false, false, 4, "fantasy", 4, …, 15000,  3, "nanora", …),
```

Single integer: `99` → `3`.

### R-3 — `:150`

```diff
-        int unlockRequirement; // stage order required, -1 default, 99 = never.
+        int unlockRequirement; // stage order required; -1 and 99 mean always unlocked (see FullGameCanvas.stageUnlocked).
```

The old comment claimed `99 = never`, which contradicted `FullGameCanvas.stageUnlocked:286`, which returns `true` for `99`. The new comment states actual behaviour and points at the implementing method.

**`stageUnlocked` was not modified** — as instructed, the value `3` satisfies the requirement under existing logic.

---

## 4. Files Changed

| File | Change | Lines |
|------|--------|-------|
| `ContentCatalog.java` | `:150` comment, `:269` one integer | +2 / −2 |

Cumulative working tree: 4 files, +83 / −23 (was +81 / −21; delta is exactly the two R-1/R-3 line replacements).

`AssetLibrary.java`, `FullGameCanvas.java`, `WikiCatalogData.java` untouched by this step.

---

## 5. Static Verification

### 5.1 Unlock chain — read from compiled bytecode

Values extracted from the built JAR by locating each `coinClear` operand and reading the immediately following push (the `unlockRequirement` argument):

| Stage | `coinClear` | Compiled `unlockRequirement` | Opcode |
|-------|-------------|------------------------------|--------|
| Stage 1 | 3000 | **-1** | `iconst_m1` |
| Stage 2 | 5000 | **0** | `iconst_0` |
| Stage 3 | 7500 | **1** | `iconst_1` |
| Stage 4 | 10000 | **2** | `iconst_2` |
| Stage 5 | 15000 | **3** | `iconst_3` (was `bipush 99`) |

**Stage 1 available on a fresh install** — `-1` short-circuits before any save read (`FullGameCanvas:286`).
**Stages 2-5 each require the previous stage** — chain `-1 → 0 → 1 → 2 → 3`, strictly forward, no cycle.

### 5.2 Save format unchanged

Verified in the built JAR's `SaveStore.class`:

| Item | Value | Status |
|------|-------|--------|
| Store name | `holojava_fullgame_v1` | unchanged |
| `RECORD_ID` / `DATA_LEN` / `SETTINGS_INDEX` / `LEGACY_LEN` | fields all present | unchanged |
| Allocation | `bipush 48` (×2) | unchanged |
| Record id on write | `iconst_1` | unchanged |
| `isCompleted` stage bound | `bipush 7` | unchanged |
| Version byte / checksum | absent | **not added** |

**Bitmask unchanged**: stage orders 0-4 occupy bits 0-4 of `data[characterIndex]`, well inside the existing `<= 7` bound.

### 5.3 Test results

| Check | Result |
|-------|--------|
| `./tools/build_fullgame.sh` | ✅ **PASS** — JAR 510473 bytes |
| `python3 tools/verify_fullgame_build.py` | ✅ **PASS** — `assets=166, wiki rows verified` |
| `git diff --check` | ✅ clean |

---

## 6. Expected Unlock Table (static evaluation)

| Persisted bits | S1 | S2 | S3 | S4 | S5 |
|----------------|----|----|----|----|----|
| *(fresh install)* | ✅ | 🔒 | 🔒 | 🔒 | 🔒 |
| after Stage 1 | ✅ | ✅ | 🔒 | 🔒 | 🔒 |
| after Stage 2 | ✅ | ✅ | ✅ | 🔒 | 🔒 |
| after Stage 3 | ✅ | ✅ | ✅ | ✅ | 🔒 |
| after Stage 4 | ✅ | ✅ | ✅ | ✅ | ✅ |

Strictly sequential, no skips. Each state persists via the existing `setCompleted` → `setRecord` path.

---

## 7. Boundary Compliance

| Boundary | Status |
|----------|--------|
| `SaveStore` untouched | ✅ not in diff |
| Record size / ID / format / migration / legacy policy untouched | ✅ verified §5.2 |
| No version byte, no checksum added | ✅ |
| Victory system and `setCompleted` call untouched | ✅ `FullGameCanvas:149`, `RunWorld:687-694` unchanged |
| Modes, character roster, Skill/Special, items, collabs untouched | ✅ |
| `stageUnlocked` not modified | ✅ |
| No game / MIDlet / emulator | ✅ |
| No download / install | ✅ |
| No commit / reset / stash / history rewrite | ✅ |
| A1 / A2 / A4a preserved | ✅ |
| Reference JAR unchanged | ✅ |

---

## 8. Unverified Limitations

| # | Limitation |
|---|-----------|
| U1 | **No runtime confirmation.** The unlock chain is proven by source reading and compiled-bytecode inspection only; no stage was played (game execution prohibited). |
| U2 | Legacy 6-byte record corruption (A4b finding F2) is **unchanged and still present** — deliberately out of scope; it remains R-2. |
| U3 | `99` is now unused by any stage but is still honoured by `stageUnlocked:286`. Removing the sentinel would change `stageUnlocked`, which this step was told not to touch. |
| U4 | Progress remains per-character; the table above holds for whichever character is selected. |

---

## 9. Conclusion

Stage 5 now requires Stage 4 (`unlockRequirement 99 → 3`), completing a strictly sequential Stage 1-5 chain. The save format, bitmask, persistence path, and victory logic are all untouched — the change is one catalog integer plus one corrected comment.

**A4b-R1/R3 complete. Awaiting approval before R-2 or any other change.**
