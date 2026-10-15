# HoloJava — Tahap 3B: Final Gate Sebelum Implementasi (READ-ONLY)

## Baseline
| Item | Status |
|------|--------|
| Reports reviewed | `04_CRITICAL_VALIDATION.md`, `05_REVISED_DESIGN.md` |
| Source inspected | `SaveStore.java`, `FullGameCanvas.java:147-152,251-262`, `ContentCatalog.java:252-263` |
| Git HEAD | `666e338` (unverified this run — read-only gate) |

---

## 1. Unlock Mapping — VERIFIED AGAINST `stageUnlocked()`

### 1.1 Actual Condition (Source: `FullGameCanvas.java:257-262`)

```java
private boolean stageUnlocked(int index) {
    ContentCatalog.StageDef s = ContentCatalog.stage(index);
    if (s.unlockRequirement == -1 || s.unlockRequirement == 99) return true;   // L259
    if (s.timeStage) return save.isCompleted(characterIndex, 2);              // L260
    return save.isCompleted(characterIndex, s.unlockRequirement);              // L261
}
```

### 1.2 Semantics of `unlockRequirement` (FACT)

`StageDef` field comment (`ContentCatalog.java:150`): `// stage order required, -1 default, 99 = never.`

`FullGameCanvas.java:149` writes with `ContentCatalog.stage(stageIndex).order` → `SaveStore.setCompleted(char, stage.order)`.

**Therefore `unlockRequirement` is compared against a stage ORDER, not an array index.** Confirmed by `L261` passing it directly to `isCompleted(characterIndex, stageOrder)`.

### 1.3 Current vs Required Values

| Stage ID | `order` | Current `unlockRequirement` | Reads As | Required | Status |
|----------|---------|------------------------------|----------|----------|--------|
| `stage1` | 0 | -1 | always unlocked | **-1** | ✅ Correct |
| `stage2` | 1 | 0 | `isCompleted(0)` = Stage 1 done | **0** | ✅ Correct |
| `stage3` | 2 | 1 | `isCompleted(1)` = Stage 2 done | **1** | ✅ Correct |
| `stage4` | 3 | 2 | `isCompleted(2)` = Stage 3 done | **2** | ✅ Correct |
| `stage5` | 4 | **99** | always unlocked (L259) | **3** | ❌ **WRONG** |

### 1.4 Correction to Prior Reports

`00_DESIGN_SUMMARY.md`, `03_SIMPLE_PROGRESSION_DESIGN.md` (L175) and `05_REVISED_DESIGN.md` all specify `unlockRequirement = 4` for `stage5`.

**That value is incorrect.** `4` would test `isCompleted(char, 4)` — i.e. *"is stage5 itself already complete"* — producing a **circular lock** (stage5 can never unlock).

Correct value is **`3`** (previous stage's `order`).

### 1.5 Circular Lock Check (Required Values)

```
stage1 (-1)  → open from start        [no dependency]
stage2 (0)   → needs stage1 order 0   [complete]
stage3 (1)   → needs stage2 order 1   [complete]
stage4 (2)   → needs stage3 order 2   [complete]
stage5 (3)   → needs stage4 order 3   [complete]
```
No cycle. Dependency graph is a strict chain ending at `-1`.

### 1.6 Unresolved: `99` Semantics Contradiction

`ContentCatalog.java:150` comment says `99 = never`, but `FullGameCanvas.java:259` treats `99` as **always unlocked**. Comment and code disagree. Not blocking (stage5 no longer uses 99), but should be corrected in a separate change.

**Status: NEEDS REVISION** — 1 value change: `stage5.unlockRequirement` `99 → 3` (not `4`).

---

## 2. Recovery Invariant — TWO DEFECTS FOUND IN `05_REVISED_DESIGN.md`

### 2.1 Required Invariant

> Temporary record must not be deleted until the main record is valid and verified by read-back.

### 2.2 Defect A — Temp Record Format Mismatch (BLOCKER)

**Migration path** (`migrateIfNeeded()`):
```java
byte[] migrated = transposeLegacy(loaded);          // length 48, NO version byte
store.setRecord(TEMP_RECORD_ID, migrated, 0, migrated.length);
```

**Recovery path** (`load()`):
```java
byte[] temp = store.getRecord(TEMP_RECORD_ID);
if (temp != null && isCurrentFormat(temp)) { ... }   // requires length 49 + d[0]==2
```

`isCurrentFormat` requires `d.length == DATA_LEN + 1` (49). Migration writes **48 bytes**. `save()` writes **49 bytes** (with version prefix).

**Consequence**: recovery check **never matches** a migration temp record. The recovery path in `05_REVISED_DESIGN.md` is dead code for migrations — the exact scenario it was designed for.

**Fix**: migration must build the same versioned record as `save()`:
```java
byte[] migrated = transposeLegacy(loaded);            // 48 bytes internal data
byte[] record = new byte[DATA_LEN + 1];
record[0] = (byte) SAVE_VERSION;
System.arraycopy(migrated, 0, record, 1, DATA_LEN);
store.setRecord(TEMP_RECORD_ID, record, 0, record.length);
// verify + promote + verify + delete
```

### 2.3 Defect B — Temp Deleted Before Main Verified (BLOCKER)

**Current** (`migrateIfNeeded()` and `save()`, identical pattern):
```java
store.setRecord(RECORD_ID, record, 0, record.length);   // main write
store.deleteRecord(TEMP_RECORD_ID);                     // temp deleted — NO main verify
```

**Consequence**: if the main write silently corrupts or throws after partial write, the temp safety net is already gone. Invariant violated.

**Fix — required sequence**:
```java
// 1. Write temp
store.setRecord(TEMP_RECORD_ID, record, 0, record.length);
// 2. Verify temp
byte[] tv = store.getRecord(TEMP_RECORD_ID);
if (tv == null || !arraysEqual(record, tv)) return;      // abort — main untouched
// 3. Write main
store.setRecord(RECORD_ID, record, 0, record.length);
// 4. Verify main
byte[] mv = store.getRecord(RECORD_ID);
if (mv == null || !arraysEqual(record, mv)) return;      // abort — temp RETAINED for recovery
// 5. Only now delete temp
store.deleteRecord(TEMP_RECORD_ID);
```

### 2.4 Corrected Operation Order (Legacy → Temp → Main)

| Step | Operation | On Failure |
|------|-----------|------------|
| 1 | Read main (ID 1), validate legacy | Abort. Nothing written. Legacy intact. |
| 2 | Transpose + validate | Abort. Nothing written. |
| 3 | `setRecord(TEMP=2, v2)` | Abort. Legacy intact, temp may be partial (ignored by `isCurrentFormat`). |
| 4 | `getRecord(2)` verify byte-equal | Abort. Legacy intact. Temp retained for diagnostics. |
| 5 | `setRecord(1, v2)` | Abort. **Temp retained.** Legacy may be corrupt — recovered next launch from temp. |
| 6 | `getRecord(1)` verify byte-equal | Abort. **Temp retained.** Recovery path restores. |
| 7 | `deleteRecord(2)` | Non-fatal. Stale temp; next `save()` overwrites it. |

### 2.5 Recovery Behavior (`load()`)

| Main (ID 1) | Temp (ID 2) | Action |
|-------------|-------------|--------|
| valid v2 | absent | Load main. Normal. |
| legacy v1 | absent | Transpose in memory. Do not write. |
| absent/corrupt | valid v2 | **Promote**: `setRecord(1, temp)` → verify → `deleteRecord(2)` → load. |
| absent/corrupt | invalid | Defaults. Do not write. |
| valid v2 | valid v2 | Main wins; delete stale temp. |

**Status: NEEDS REVISION** — 2 blockers (A: format mismatch, B: premature temp deletion).

---

## 3. Additional Finding — Settings Bit Collision in Source

`SaveStore.java:44-49`:
```java
int selectedCharacter() { return data[SETTINGS_INDEX] & 0x3f; }        // bits 0-5
void setSelectedCharacter(int value) {
    int settings = data[SETTINGS_INDEX] & 0xc0;                        // keeps bits 6-7 only
    data[SETTINGS_INDEX] = (byte) (settings | (value & 0x3f));          // overwrites bits 0-5
}
```
`hardcore()` occupies **bit 0**, which lies inside the `0x3f` mask written by `setSelectedCharacter`. Calling it **clears the hardcore flag**.

This is independent of the migration but affects the settings byte that migration copies verbatim.

**Status: UNRESOLVED** — out of scope for this gate; flagged for separate change.

---

## Final Recommendation

**DO NOT IMPLEMENT `05_REVISED_DESIGN.md` AS WRITTEN.** It contains 3 defects that would defeat its own safety guarantees:

| # | Defect | Severity | Fix |
|---|--------|----------|-----|
| 1 | `stage5.unlockRequirement` should be **3**, not 4 (4 = circular lock) | **BLOCKER** | 1 value in `ContentCatalog.java:257` |
| 2 | Migration temp record is 48 bytes; recovery expects 49 — recovery is dead code | **BLOCKER** | Wrap migration payload in version prefix |
| 3 | Temp deleted before main read-back verification | **BLOCKER** | Add step 6 verify-main before step 7 delete-temp |

After applying all 3 fixes, the design satisfies: Stage 1 open from start, sequential unlock with no cycle, legacy never lost, and a working crash-recovery path.

---

## Remaining Assumptions

| # | Assumption | Risk if False |
|---|-----------|---------------|
| 1 | `CHARACTERS.length >= 1` — `WikiCatalogData.buildCharacters()` returns `new CharacterDef[0]` (stub, `WikiCatalogData.java:165-167`). With length 0, `isCompleted`/`setCompleted` reject **every** index → progression cannot function at all | **HIGH** — blocks entire feature |
| 2 | Legacy settings-byte bit layout identical between formats | Medium — report `01_BYTE_MAPPING_EVIDENCE.md` asserts `0x3F` on both, but its own `02_REFERENCE_BYTECODE_LAYOUT.md` shows `selectedCharacter()` using `bipush 15` (`0x0F`). Unreconciled |
| 3 | `unlockRequirement == 99` unreachable after fix | Low — comment/code contradiction remains |
| 4 | Single-record RMS store, no concurrent MIDlet access | Low — not verified in source |

**Assumption 1 is a hard precondition**: no save/progression change works until the character catalog returns a non-empty array.
