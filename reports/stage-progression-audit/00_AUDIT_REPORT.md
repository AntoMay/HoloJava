# A4b — Stage Unlock & Progress Persistence Audit (READ-ONLY)

## 1. Baseline

| Item | Expected | Actual | Status |
|------|----------|--------|--------|
| Git HEAD | `666e338` | `666e338` | ✅ |
| A1 + A2 + A4a changes | present | present, unmodified (4 files, +81/−21) | ✅ PRESERVED |
| Reference JAR SHA-256 | `20b31de6…3a6dfb` | identical, read-only | ✅ |

Line numbers below reflect the working tree **after** A1/A2/A4a. JAR evidence comes from `javap -c -p` on an extraction in `/tmp`; the reference JAR was never written.

---

## 2. Flow Map — Selection to Save/Load

```
BOOT
  FullGameCanvas:58   characterIndex = save.selectedCharacter()
  FullGameCanvas:59   clamp to [0, CHARACTERS.length)          -> 0..4
  SaveStore:18-21     new byte[48] zeroed, then load()

LOAD
  SaveStore:55        openRecordStore("holojava_fullgame_v1", false)
  SaveStore:57        getRecord(1)
  SaveStore:59-65     legacy(6B) | partial | full(48B) copy
  SaveStore:67-72     RecordStoreException -> defaults kept

STAGE LIST  (A4a)
  FullGameCanvas:221-235  selectableStages() -> catalog indices [0,1,2,3,4]

NAVIGATE
  FullGameCanvas:212      wrap(menuIndex +/- 1, stages.length + 1)   -> 0..5

CONFIRM
  FullGameCanvas:216      if (!stageUnlocked(c) || !stageAllowed(c)) return;
  FullGameCanvas:217      stageIndex = candidate                      (real catalog index)
  FullGameCanvas:218      screen = PREP
  FullGameCanvas:253-262  updatePrep LEFT -> map stageIndex back to slot
  FullGameCanvas:272      startRun() re-checks unlock + allowed        (second gate)
  FullGameCanvas:273      new RunWorld(..., stageIndex)

VICTORY
  RunWorld:687-694   mode.completion == COMPLETE_FINAL_BOSS
                     && encounterDefeated[stage.hard ? 2 : 1]
                     -> won = true; state = STATE_VICTORY

PERSIST
  FullGameCanvas:147-149  worldWasVictory && modeIndex == 0
                          -> save.setCompleted(characterIndex, stage(stageIndex).order)
  SaveStore:29-34         bounds check; data[char] |= (1 << stageOrder); save()
  SaveStore:77-91         openRecordStore(..., true)
  SaveStore:81            setRecord(1, data, 0, 48)

READ BACK (next launch)
  SaveStore:63-64         count = min(loaded.length, 48); byte copy
  SaveStore:25            isCompleted: data[char] & (1 << stageOrder)
```

---

## 3. Findings

### 3.1 Stage definitions — `ContentCatalog.java:264-274`

| Idx | Id | Name | `order` | `hard` | `timeStage` | `unlockRequirement` |
|-----|-----|------|---------|--------|-------------|---------------------|
| 0 | `stage1` | Grassy Plains | 0 | false | false | **-1** |
| 1 | `stage2` | Holo Office | 1 | false | false | **0** |
| 2 | `stage3` | Halloween Castle | 2 | false | false | **1** |
| 3 | `stage4` | Gelora Bung Yagoo | 3 | false | false | **2** |
| 4 | `stage5` | Fantasy Island | 4 | false | false | **99** ⚠️ |
| 5-8 | `stage1h`..`stage4h` | Hard variants | 5-8 | **true** | false | 1, 5, 6, 7 |
| 9 | `time1` | Concert Stage | 9 | false | **true** | 2 |

`StageDef.unlockRequirement` is documented as `// stage order required, -1 default, 99 = never.` (`ContentCatalog.java:150`).

### 3.2 Unlock evaluation — `FullGameCanvas.java:284-289`

```java
private boolean stageUnlocked(int index) {
    ContentCatalog.StageDef s = ContentCatalog.stage(index);
    if (s.unlockRequirement == -1 || s.unlockRequirement == 99) return true;   // :286
    if (s.timeStage) return save.isCompleted(characterIndex, 2);              // :287
    return save.isCompleted(characterIndex, s.unlockRequirement);              // :288
}
```

`unlockRequirement` is a **stage order**, not an array index — `:149` persists with `stage(stageIndex).order`, and `:288` passes the value straight to `isCompleted(characterIndex, stageOrder)`.

### 3.3 F1 (HIGH) — `stage5` is permanently unlocked

`stage5.unlockRequirement == 99` satisfies `:286` → returns `true` without consulting the save.

**Important nuance — this is NOT a source regression.** The reference JAR ships the identical logic:

```
FullGameCanvas.stageUnlocked (JAR bytecode)
   6: getfield unlockRequirement
   9: iconst_m1; if_icmpeq 22
  17: bipush 99;      if_icmpne 24
  22: iconst_1; ireturn          // -1 OR 99 -> true
```

and the JAR's `stage5` also carries `bipush 99` at offset 2159. So `99 → always open` is the **reference implementation's own behavior**.

The defect is narrower: `ContentCatalog.java:150` documents `99` as *"never"*, which **contradicts** `:286`. The comment is wrong, or the value is wrong — the audit cannot determine intent from source alone.

### 3.4 F2 (HIGH, latent) — legacy 6-byte record is misread

`SaveStore.java:59-61`:
```java
if (loaded.length == LEGACY_LEN) {          // 6
    for (int i = 0; i < 5; i++) data[i] = loaded[i];
    data[SETTINGS_INDEX] = loaded[5];
}
```

Reference JAR `SaveStore.isCompleted` (bytecode):
```
 1: iload_1            // stageOrder is the FIRST parameter
 5: iconst_5; if_icmplt
15: iload_1
16: baload             // data[stageOrder]
24: iload_2            // characterIndex
25: ishl              // (1 << characterIndex)
```

| | Layout | Array index | Bit position | Stage bound |
|---|---|-------------|--------------|-------------|
| Reference JAR | stage-major, 6 B | `stageOrder` | `characterIndex` | `< 5` |
| Current source | character-major, 48 B | `characterIndex` | `stageOrder` | `<= 7` |

The layouts are **transposed**. A legacy record's stage-0 bitmask is copied into `data[0]`, which the source reads as *character 0's* stage bitmask. Result is silent progress corruption, not a crash.

### 3.5 F3 (MEDIUM) — save record has no version or integrity marker

`SaveStore.java:10-16`: store `"holojava_fullgame_v1"`, `RECORD_ID = 1`, `DATA_LEN = 48`, no version byte, no checksum. `save()` (`:81`) always writes `data.length` = 48 bytes.

Format is distinguished **only by length**. A future layout change would be indistinguishable from the current one.

### 3.6 F4 (INFO) — progress is per-character

`SaveStore.java:25,32` index `data[characterIndex]`. The five verified characters hold five independent bitmasks in `data[0..4]`. Stage unlock is therefore evaluated against the currently selected character only.

### 3.7 Stage orders 0-4 fit the current bit layout — no format change needed

`SaveStore.java:30` allows `stageOrder <= 7`; orders 0-4 occupy bits 0-4 of one byte. Stage 5 completion (`order 4`) is fully representable today.

---

## 4. Unlock Status Table

Evaluated with `stageUnlocked()` against `isCompleted(char, order)`; `modeIndex == 0` (Stage Mode, pinned by A4a).

### 4.1 Fresh install (no save record)

| Stage | `unlockRequirement` | Test performed | Unlocked? |
|-------|---------------------|----------------|-----------|
| Stage 1 (Grassy Plains) | -1 | short-circuit | ✅ open |
| Stage 2 (Holo Office) | 0 | `isCompleted(c,0)` false | 🔒 locked |
| Stage 3 (Halloween Castle) | 1 | `isCompleted(c,1)` false | 🔒 locked |
| Stage 4 (Gelora Bung Yagoo) | 2 | `isCompleted(c,2)` false | 🔒 locked |
| **Stage 5 (Fantasy Island)** | 99 | short-circuit | ⚠️ **OPEN — F1** |

### 4.2 Progression after each victory

| Persisted bits | Stage 1 | Stage 2 | Stage 3 | Stage 4 | Stage 5 |
|----------------|---------|---------|---------|---------|---------|
| *(fresh)* | ✅ | 🔒 | 🔒 | 🔒 | ⚠️ open |
| after Stage 1 | ✅ | ✅ | 🔒 | 🔒 | ⚠️ open |
| after Stage 2 | ✅ | ✅ | ✅ | 🔒 | ⚠️ open |
| after Stage 3 | ✅ | ✅ | ✅ | ✅ | ⚠️ open |
| after Stage 4 | ✅ | ✅ | ✅ | ✅ | ⚠️ open |
| after Stage 5 | ✅ | ✅ | ✅ | ✅ | ⚠️ open |

**Sequence is strictly forward-ordered and cannot be skipped**, except Stage 5 which is reachable from the very first launch.

### 4.3 Survival across restart

`setCompleted` → `save()` → `setRecord(1, data, 0, 48)` writes immediately; `load()` copies all 48 bytes back. Bits 0-4 of `data[characterIndex]` round-trip intact.

**Conclusion: yes — Stage 2-5 unlock sequentially and remain unlocked after restart**, with the sole exception of Stage 5, which is open from the start.

---

## 5. Old / Invalid / Incomplete Record Behaviour

`SaveStore.load()` (`:52-75`), all outcomes of `getRecord(RECORD_ID)`:

| Record | Branch | Result | Progression impact |
|--------|--------|--------|--------------------|
| `null` | `:58` false | defaults (all zero) | fresh-install state ✅ |
| 0-5 bytes | `:62-64` | partial copy into `data[0..len-1]` | interpreted as character bitmasks — **silent corruption** ⚠️ |
| **6 bytes (JAR save)** | `:59-61` | `loaded[0..4] → data[0..4]`, `loaded[5] → data[47]` | **transposed — silent corruption** ⚠️ (F2) |
| 7-47 bytes | `:62-64` | partial copy; `data[47]` stays 0 | settings (hardcore, selected character) **lost** |
| 48 bytes | `:62-64` | full copy | correct ✅ |
| >48 bytes | `:62-64` | truncated to 48 | extra bytes discarded |
| RMS open/get throws | `:67-72` | defaults kept | fresh-install state, no crash ✅ |

No path throws. No path writes during load. **A corrupt or legacy record is never overwritten by loading itself** — the first `setCompleted`/`setHardcore`/`setSelectedCharacter` would overwrite it with the (corrupt) in-memory state.

---

## 6. Can Sequential Progression Be Applied Without Changing the Save Format?

**Yes.**

| Requirement | Met? | Evidence |
|-------------|-----|----------|
| Stage orders 0-4 representable | ✅ | bits 0-4 of `data[char]`; bound is `<= 7` (`SaveStore.java:30`) |
| `unlockRequirement` is catalog metadata, not save data | ✅ | `ContentCatalog.java:265-269` |
| Persistence already works | ✅ | §4.3 |
| `stageUnlocked` already consults the save | ✅ | `FullGameCanvas.java:288` |

Enforcing Stage 5 behind Stage 4 requires editing **one integer** in `ContentCatalog.java:269` (`99` → `3`). **No save format change, no migration, no new record shape.**

Caveat: this changes shipped reference behavior (F1 nuance in §3.3), so it is a design decision, not a bug fix.

---

## 7. Risk Register

| ID | Risk | Severity | Likelihood | Evidence |
|----|------|----------|------------|----------|
| R1 | Stage 5 reachable without clearing Stages 1-4 | **HIGH** | Certain on fresh install | `ContentCatalog.java:269` + `:286` |
| R2 | Legacy 6-byte save silently corrupts progress | **HIGH** | Only for users upgrading from the JAR | `SaveStore.java:59-61` vs JAR `isCompleted` |
| R3 | No version byte — future layout change indistinguishable | MEDIUM | On any future format change | `SaveStore.java:10-16, 81` |
| R4 | 7-47 byte record loses the settings byte | MEDIUM | Corruption only | `SaveStore.java:63-64` |
| R5 | First write after a bad load overwrites it | LOW | Corruption only | `SaveStore.java:33, 41, 49` |
| R6 | `99` comment contradicts code semantics | LOW | Always | `ContentCatalog.java:150` vs `:286` |

---

## 8. Recommendations (dependency-ordered)

### R-1 — Enforce sequential unlock for Stage 5 *(no dependencies)*
- **File:** `ContentCatalog.java:269`
- **Change:** `unlockRequirement` `99` → `3`
- **Dependency:** none
- **Regression risk:** LOW — one integer; `order` values are untouched; the bit layout already supports order 4
- **Test:** fresh install shows only Stage 1 open; clear 1→2→3→4→5 in order; each persists across restart
- **Note:** this is a deliberate departure from reference behavior; confirm intent first

### R-2 — Decide legacy-record policy *(no code dependency on R-1)*
- **File:** `SaveStore.java:52-75`
- **Change (option A):** reject 6-byte records — keep defaults, do not overwrite
- **Change (option B):** transpose `L[stage] bit char → S[char] bit stage`, then rewrite
- **Dependency:** decision only
- **Regression risk:** MEDIUM — option B touches the only persistence path
- **Test:** seed a known 6-byte record; verify either clean defaults or correct transpose; confirm the original is not silently lost
- **Note:** any real implementation also needs the atomic-write/recovery design from `reports/stage-progression-design/06_FINAL_GATE.md`

### R-3 — Reconcile the `99` comment *(no dependencies)*
- **File:** `ContentCatalog.java:150`
- **Change:** update the comment to match whichever semantics R-1 selects
- **Dependency:** R-1

### R-4 — Add a record version byte *(depends on R-2)*
- **File:** `SaveStore.java`
- **Dependency:** R-2 must be settled first; otherwise version numbering is arbitrary
- **Regression risk:** MEDIUM

### R-5 — Add record integrity validation *(depends on R-4)*
- Skip for now — R-2 option A provides most of the benefit with far less risk

---

## 9. Unverified Items

| # | Item | Why unverified |
|---|------|----------------|
| U1 | Actual on-device RMS behaviour (record size limits, `setRecord` atomicity) | no emulator run permitted; no RMS stub introspection |
| U2 | Whether any real user holds a 6-byte record | no save data available; JAR shipping history not in scope |
| U3 | Whether `99` was *intended* as "never" or as "free access" | both the comment (`:150`) and the code (`:286`) are plausible; the JAR agrees with the code |
| U4 | End-to-end victory in a real run | no runtime execution permitted |

---

## 10. Conclusion

Sequential progression **is already implemented and persists correctly** for Stages 1-4. Stage 5 is the single break in the chain, caused by one catalog value.

Fixing it requires **no save-format change**. The legacy-record corruption (F2) is a separate, pre-existing defect that should be decided independently.

**No code was modified. A4b is read-only and complete. Awaiting approval before any change.**
